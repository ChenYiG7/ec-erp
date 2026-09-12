package com.own.erp.finance.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.PurchaseQueryApi.PurchasePayableView;
import com.own.erp.finance.constant.PaymentConsts;
import com.own.erp.finance.entity.PaymentAlloc;
import com.own.erp.finance.entity.PaymentRecord;
import com.own.erp.finance.entity.SettlementReport;
import com.own.erp.finance.mapper.PaymentAllocMapper;
import com.own.erp.finance.mapper.PaymentQueryMapper;
import com.own.erp.finance.mapper.PaymentRecordMapper;
import com.own.erp.finance.request.command.ManualPaymentRequest;
import com.own.erp.finance.request.command.PurchasePaymentRequest;
import com.own.erp.finance.response.PaymentAllocResponse;
import com.own.erp.finance.response.PaymentDetailResponse;
import com.own.erp.finance.response.PaymentRecordResponse;
import com.own.erp.finance.response.PaymentSummaryResponse;
import com.own.erp.finance.response.PaymentSummaryRow;
import com.own.erp.finance.response.PlatformReceiptRow;
import com.own.erp.finance.response.PurchasePaidRow;
import com.own.erp.finance.response.SupplierPayableRow;
import com.own.erp.finance.request.query.PaymentRecordQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水服务(#31 收付款/回款,docs/plans/payment-receipt.md):payment_record(+payment_alloc)
 *     域整域收口,Controller 不直连 Mapper(docs/07 §2.1),entity 不出本层。
 *     统一资金账——付款 EXPENSE/回款 INCOME 一张表;三个写入口:
 *     ①registerPurchasePayment 采购付款(强制 CNY,经采购只读契约 PurchaseQueryApi 校验存在/状态/总额,
 *       未审核 DRAFT 不可付;按单"已付+本次≤总额"超额拦截;Σ分摊≤流水金额,允许部分挂账;往来方取采购单供应商);
 *     ②registerManual 手工补录(非 CNY 按 paidAt 回溯 resolveRate 冻结,缺汇率 amountCny 留 NULL);
 *     ③deriveSettlementReceipt 结算报告落 PARSED 同事务自动派生(uk_ref 幂等,重拉覆盖同步刷新;禁 AFTER_COMMIT 追赶记)。
 *     作废制:NORMAL→VOIDED 条件更新留痕,禁物理删;分摊行不删,join NORMAL 自然失效。
 *     采购单已付 = Σ NORMAL 流水分摊,查询时聚合不冗余(拍板点①,防漂移);跨域只走契约,禁横向依赖(铁律 2)
 */
@Slf4j
@Service
public class PaymentRecordService {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SEQ_RETRY_LIMIT = 5;

    /** 采购单草稿态词面(词表源 = erp-purchase PurchaseConsts.PO_DRAFT 与契约视图 javadoc,跨域不引模块常量) */
    private static final String PO_STATUS_DRAFT = "DRAFT";

    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentAllocMapper paymentAllocMapper;
    private final PaymentQueryMapper paymentQueryMapper;
    private final ExchangeRateService exchangeRateService;
    private final PurchaseQueryApi purchaseQueryApi;
    private final CurrentUserApi currentUserApi;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service,急切装配成环(docs/07 §2.2) */
    public PaymentRecordService(PaymentRecordMapper paymentRecordMapper,
                                PaymentAllocMapper paymentAllocMapper,
                                PaymentQueryMapper paymentQueryMapper,
                                ExchangeRateService exchangeRateService,
                                @Lazy PurchaseQueryApi purchaseQueryApi,
                                @Lazy CurrentUserApi currentUserApi) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.paymentAllocMapper = paymentAllocMapper;
        this.paymentQueryMapper = paymentQueryMapper;
        this.exchangeRateService = exchangeRateService;
        this.purchaseQueryApi = purchaseQueryApi;
        this.currentUserApi = currentUserApi;
    }

    // ============================ 查询面 ============================

    /** 流水分页(XML join 往来方名;默认滤 VOIDED,对账口径显式 includeVoided) */
    public Page<PaymentRecordResponse> page(PaymentRecordQuery query) {
        return paymentQueryMapper.pageRows(new Page<>(query.getPageNo(), query.pageSize()),
                query.getDirection(), query.getBizType(), query.getPartyType(), query.getPartyId(),
                query.getPaidFrom(), query.getPaidTo(), query.getIncludeVoided());
    }

    /** 流水详情(带采购分摊行,poNo 经契约批量回填;结算/手工流水分摊为空列表);不存在返回 null */
    public PaymentDetailResponse getDetail(Long id) {
        PaymentRecord record = paymentRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        List<PaymentAlloc> allocs = paymentAllocMapper.selectList(new LambdaQueryWrapper<PaymentAlloc>()
                .eq(PaymentAlloc::getPaymentId, id)
                .orderByAsc(PaymentAlloc::getId));
        Map<Long, String> poNoById = loadPoNoMap(allocs);
        List<PaymentAllocResponse> allocResponses = allocs.stream()
                .map(a -> PaymentAllocResponse.builder()
                        .id(a.getId())
                        .poId(a.getAllocBizId())
                        .poNo(poNoById.get(a.getAllocBizId()))
                        .amount(a.getAmount())
                        .build())
                .toList();
        return PaymentDetailResponse.builder()
                .payment(PaymentRecordResponse.from(record))
                .allocs(allocResponses)
                .build();
    }

    /** 采购单已付批量(采购列表/详情资金视图):Σ NORMAL 流水分摊;无分摊的单补 0 行 */
    public List<PurchasePaidRow> listPurchasePaid(Collection<Long> poIds) {
        if (CollUtil.isEmpty(poIds)) {
            return List.of();
        }
        Map<Long, PurchasePaidRow> byId = new HashMap<>();
        for (PurchasePaidRow row : paymentQueryMapper.sumPaidByPoIds(poIds)) {
            byId.put(row.poId(), row);
        }
        List<PurchasePaidRow> result = new ArrayList<>();
        for (Long poId : poIds) {
            result.add(byId.getOrDefault(poId,
                    PurchasePaidRow.builder().poId(poId).paidAmount(BigDecimal.ZERO).build()));
        }
        return result;
    }

    /** 供应商应付/已付/待付分页(待付 Java 侧算,避免 SQL 同层列别名互引) */
    public Page<SupplierPayableRow> pageSupplierPayables(Integer pageNo, Integer pageSize) {
        Page<SupplierPayableRow> page = paymentQueryMapper.pageSupplierPayables(new Page<>(nvlPageNo(pageNo), nvlPageSize(pageSize)));
        page.setRecords(page.getRecords().stream()
                .map(row -> SupplierPayableRow.builder()
                        .supplierId(row.supplierId())
                        .supplierName(row.supplierName())
                        .settleDays(row.settleDays())
                        .payableAmount(row.payableAmount())
                        .paidAmount(row.paidAmount())
                        .unpaidAmount(row.payableAmount().subtract(row.paidAmount()))
                        .build())
                .toList());
        return page;
    }

    /** 平台回款聚合(按 settle 期间;缺汇率行只计数) */
    public List<PlatformReceiptRow> listPlatformReceipts(LocalDateTime paidFrom, LocalDateTime paidTo) {
        return paymentQueryMapper.listPlatformReceipts(paidFrom, paidTo);
    }

    /** 期间收付净额汇总(CNY;缺汇率行进缺口计数,金额不静默归零) */
    public PaymentSummaryResponse summary(LocalDateTime paidFrom, LocalDateTime paidTo) {
        PaymentSummaryRow row = paymentQueryMapper.summary(paidFrom, paidTo);
        BigDecimal income = row == null || row.incomeCny() == null ? BigDecimal.ZERO : row.incomeCny();
        BigDecimal expense = row == null || row.expenseCny() == null ? BigDecimal.ZERO : row.expenseCny();
        return PaymentSummaryResponse.builder()
                .paidFrom(paidFrom)
                .paidTo(paidTo)
                .incomeCny(income)
                .expenseCny(expense)
                .netCny(income.subtract(expense))
                .totalCount(row == null || row.totalCount() == null ? 0L : row.totalCount())
                .missingRateCount(row == null || row.missingRateCount() == null ? 0L : row.missingRateCount())
                .build();
    }

    // ============================ 写侧:人工登记 ============================

    /**
     * 采购付款登记(同事务:一条 EXPENSE/PURCHASE_PAYMENT 流水 + N 条采购分摊)。
     * 守卫链:金额/分摊恒正且不重复 → 采购单存在 → 非 DRAFT(审核后才可付)→ 同一供应商
     * → 按单已付+本次≤采购总额(超额拦截)→ Σ分摊≤流水金额(允许部分挂账);
     * 币种强制 CNY(采购总额是本位币,禁混币),往来方取采购单供应商(防客户端串供)
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerPurchasePayment(PurchasePaymentRequest request) {
        List<PurchasePaymentRequest.Alloc> allocs = request.allocs();
        if (CollUtil.isEmpty(allocs)) {
            throw new BusinessException("采购付款分摊不能为空");
        }
        Set<Long> poIds = new HashSet<>();
        for (PurchasePaymentRequest.Alloc alloc : allocs) {
            if (alloc.poId() == null || alloc.amount() == null || alloc.amount().signum() <= 0) {
                throw new BusinessException("采购付款分摊行非法:poId/金额必填且金额大于0");
            }
            if (!poIds.add(alloc.poId())) {
                throw new BusinessException("同一采购单在一笔付款内重复分摊,请合并为一行:" + alloc.poId());
            }
        }
        BigDecimal allocSum = allocs.stream().map(PurchasePaymentRequest.Alloc::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocSum.compareTo(request.amount()) > 0) {
            throw new BusinessException("分摊合计超过付款金额(允许部分挂账,不可超额分摊):分摊" + allocSum + " 付款" + request.amount());
        }

        Map<Long, PurchasePayableView> poById = loadPayables(poIds);
        Long supplierId = null;
        Map<Long, BigDecimal> paidMap = paidMap(poIds);
        for (PurchasePaymentRequest.Alloc alloc : allocs) {
            PurchasePayableView po = poById.get(alloc.poId());
            if (po == null) {
                throw new BusinessException("采购单不存在:" + alloc.poId());
            }
            if (PO_STATUS_DRAFT.equals(po.status())) {
                throw new BusinessException("采购单未审核不可付款,请先审核:" + po.poNo());
            }
            if (supplierId == null) {
                supplierId = po.supplierId();
            } else if (!supplierId.equals(po.supplierId())) {
                throw new BusinessException("一笔付款的分摊采购单必须属于同一供应商");
            }
            BigDecimal paid = paidMap.getOrDefault(po.id(), BigDecimal.ZERO);
            if (paid.add(alloc.amount()).compareTo(po.totalAmount()) > 0) {
                throw new BusinessException("采购单分摊超额:" + po.poNo()
                        + ",总额" + po.totalAmount() + ",已付" + paid + ",本次" + alloc.amount());
            }
        }

        LocalDateTime paidAt = request.paidAt() == null ? LocalDateTime.now(PullConsts.ZONE) : request.paidAt();
        PaymentRecord record = PaymentRecord.builder()
                .direction(PaymentConsts.DIRECTION_EXPENSE)
                .bizType(PaymentConsts.BIZ_PURCHASE_PAYMENT)
                .partyType(PaymentConsts.PARTY_SUPPLIER)
                .partyId(supplierId)
                .amount(request.amount())
                .currency(ExchangeRateService.BASE_CURRENCY)
                .exchangeRate(BigDecimal.ONE)
                .amountCny(request.amount())
                .paidAt(paidAt)
                .method(StrUtil.trimToNull(request.method()))
                .status(PaymentConsts.STATUS_NORMAL)
                .remark(StrUtil.trimToNull(request.remark()))
                .createdBy(currentUserApi.currentUserId())
                .build();
        Long paymentId = insertWithGeneratedNo(record);
        for (PurchasePaymentRequest.Alloc alloc : allocs) {
            paymentAllocMapper.insert(PaymentAlloc.builder()
                    .paymentId(paymentId)
                    .allocBizType(PaymentConsts.ALLOC_PURCHASE)
                    .allocBizId(alloc.poId())
                    .amount(alloc.amount())
                    .build());
        }
        log.info("采购付款登记成功 paymentId={} amount={} 分摊{}单", paymentId, request.amount(), allocs.size());
        return paymentId;
    }

    /**
     * 手工资金登记(补录派生遗漏/非结算回款;biz_type 固定 MANUAL_ADJUST,不分摊):
     * 词表校验 direction/partyType;非 CNY 按 paidAt 回溯 resolveRate 冻结,无报价 amountCny 留 NULL
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerManual(ManualPaymentRequest request) {
        if (!PaymentConsts.DIRECTION_EXPENSE.equals(request.direction())
                && !PaymentConsts.DIRECTION_INCOME.equals(request.direction())) {
            throw new BusinessException("资金方向非法(EXPENSE/INCOME):" + request.direction());
        }
        if (!PaymentConsts.PARTY_SUPPLIER.equals(request.partyType())
                && !PaymentConsts.PARTY_PLATFORM.equals(request.partyType())
                && !PaymentConsts.PARTY_OTHER.equals(request.partyType())) {
            throw new BusinessException("往来方类型非法(SUPPLIER/PLATFORM/OTHER):" + request.partyType());
        }
        if (!PaymentConsts.PARTY_OTHER.equals(request.partyType()) && request.partyId() == null) {
            throw new BusinessException("供应商/平台往来方必须指定 partyId");
        }
        String currency = StrUtil.blankToDefault(StrUtil.trimToNull(request.currency()),
                ExchangeRateService.BASE_CURRENCY);
        LocalDateTime paidAt = request.paidAt() == null ? LocalDateTime.now(PullConsts.ZONE) : request.paidAt();
        BigDecimal rate = exchangeRateService.resolveRate(currency, paidAt);
        PaymentRecord record = PaymentRecord.builder()
                .direction(request.direction())
                .bizType(PaymentConsts.BIZ_MANUAL_ADJUST)
                .partyType(request.partyType())
                .partyId(PaymentConsts.PARTY_OTHER.equals(request.partyType()) ? null : request.partyId())
                .amount(request.amount())
                .currency(currency)
                .exchangeRate(rate)
                .amountCny(toCny(request.amount(), rate))
                .paidAt(paidAt)
                .method(StrUtil.trimToNull(request.method()))
                .status(PaymentConsts.STATUS_NORMAL)
                .remark(StrUtil.trimToNull(request.remark()))
                .createdBy(currentUserApi.currentUserId())
                .build();
        Long id = insertWithGeneratedNo(record);
        log.info("手工资金登记成功 paymentId={} direction={} amount={} {}", id, request.direction(), request.amount(), currency);
        return id;
    }

    /**
     * 作废:NORMAL→VOIDED 条件更新(WHERE 即守卫,禁先查后改 docs/07 §6.3),留痕禁物理删。
     * 分摊行不删(审计可回溯;已付聚合 join NORMAL 流水自然失效,采购待付自动还原)
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidPayment(Long id) {
        if (paymentRecordMapper.casVoid(id) == 0) {
            throw new BusinessException("作废失败:流水不存在或已作废:" + id);
        }
        log.info("资金流水作废 paymentId={}", id);
    }

    // ============================ 写侧:结算回款派生(系统写入口) ============================

    /**
     * 结算回款同事务派生(#31 §2.3):由 SettlementService.saveUnifiedSettlement 在报告落 PARSED 且
     * transfer_amount&gt;0 后于同一事务内调用——禁 AFTER_COMMIT 后补写(资金数据不追赶记)。
     * uk_ref(ref_type,ref_id,deleted) 幂等:派生行已存在 NORMAL → 随重拉覆盖刷新金额/币种/汇率/时间;
     * 已 VOIDED(人工作废)不复活;无则新建。paidAt 取报告预计打款日 depositDate,缺省按当前时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void deriveSettlementReceipt(SettlementReport report, Instant depositDate) {
        if (report == null || report.getTransferAmount() == null
                || report.getTransferAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        LocalDateTime paidAt = depositDate != null
                ? LocalDateTime.ofInstant(depositDate, PullConsts.ZONE)
                : LocalDateTime.now(PullConsts.ZONE);
        PaymentRecord existing = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getRefType, PaymentConsts.REF_SETTLEMENT_REPORT)
                .eq(PaymentRecord::getRefId, report.getId()));
        if (existing != null) {
            if (PaymentConsts.STATUS_VOIDED.equals(existing.getStatus())) {
                log.warn("结算回款派生流水已被人工作废,不复活 reportId={} paymentId={}", report.getId(), existing.getId());
                return;
            }
            BigDecimal rate = exchangeRateService.resolveRate(report.getCurrency(), paidAt);
            existing.setPartyId(report.getShopId());
            existing.setAmount(report.getTransferAmount());
            existing.setCurrency(report.getCurrency());
            existing.setExchangeRate(rate);
            existing.setAmountCny(toCny(report.getTransferAmount(), rate));
            existing.setPaidAt(paidAt);
            paymentRecordMapper.updateById(existing);
            log.info("结算回款派生流水随报告覆盖刷新 reportId={} paymentId={} amount={}",
                    report.getId(), existing.getId(), report.getTransferAmount());
            return;
        }
        BigDecimal rate = exchangeRateService.resolveRate(report.getCurrency(), paidAt);
        PaymentRecord record = PaymentRecord.builder()
                .direction(PaymentConsts.DIRECTION_INCOME)
                .bizType(PaymentConsts.BIZ_SETTLEMENT_RECEIPT)
                .partyType(PaymentConsts.PARTY_PLATFORM)
                .partyId(report.getShopId())
                .amount(report.getTransferAmount())
                .currency(report.getCurrency())
                .exchangeRate(rate)
                .amountCny(toCny(report.getTransferAmount(), rate))
                .paidAt(paidAt)
                .method("平台打款")
                .refType(PaymentConsts.REF_SETTLEMENT_REPORT)
                .refId(report.getId())
                .status(PaymentConsts.STATUS_NORMAL)
                .remark("结算报告 " + report.getSettlementId() + " 自动派生")
                .build();
        Long id = insertWithGeneratedNo(record);
        log.info("结算回款自动派生 reportId={} paymentId={} amount={} {}",
                report.getId(), id, report.getTransferAmount(), report.getCurrency());
    }

    // ============================ 内部装配 ============================

    /** 插入流水并生成流水号 PAY+yyyyMMdd+4位seq;uk 撞号(并发)换序号重试,同 ManualOrderService 先例 */
    private Long insertWithGeneratedNo(PaymentRecord record) {
        String prefix = PaymentConsts.PAYMENT_NO_PREFIX
                + LocalDate.now(PullConsts.ZONE).format(DATE_PART);
        for (int attempt = 0; attempt < SEQ_RETRY_LIMIT; attempt++) {
            long seq = nextSeq(prefix) + attempt;
            record.setPaymentNo(prefix + String.format("%04d", seq));
            try {
                paymentRecordMapper.insert(record);
                return record.getId();
            } catch (DuplicateKeyException e) {
                log.warn("流水号冲突,换序号重试 no={}", record.getPaymentNo());
            }
        }
        throw new BusinessException("流水号生成失败(当日序号冲突),请重试");
    }

    /** 当日序号 = 同前缀已有流水数 + 1(uk 冲突时由调用方换号重试) */
    private long nextSeq(String prefix) {
        Long count = paymentRecordMapper.selectCount(new LambdaQueryWrapper<PaymentRecord>()
                .likeRight(PaymentRecord::getPaymentNo, prefix));
        return (count == null ? 0L : count) + 1L;
    }

    /** 契约批量取采购单(缺 id 不在 map,由调用方判缺) */
    private Map<Long, PurchasePayableView> loadPayables(Set<Long> poIds) {
        Map<Long, PurchasePayableView> byId = new HashMap<>();
        for (PurchasePayableView view : purchaseQueryApi.findPurchasePayables(poIds)) {
            byId.put(view.id(), view);
        }
        return byId;
    }

    /** 采购单当前已付(Σ NORMAL 分摊),无分摊补 0 */
    private Map<Long, BigDecimal> paidMap(Set<Long> poIds) {
        Map<Long, BigDecimal> byId = new HashMap<>();
        for (PurchasePaidRow row : paymentQueryMapper.sumPaidByPoIds(poIds)) {
            byId.put(row.poId(), row.paidAmount());
        }
        return byId;
    }

    /** 分摊行 → poId/poNo 映射(契约批量取数,禁逐行调用) */
    private Map<Long, String> loadPoNoMap(List<PaymentAlloc> allocs) {
        Set<Long> poIds = new HashSet<>();
        for (PaymentAlloc alloc : allocs) {
            if (PaymentConsts.ALLOC_PURCHASE.equals(alloc.getAllocBizType())) {
                poIds.add(alloc.getAllocBizId());
            }
        }
        Map<Long, String> byId = new HashMap<>();
        for (PurchasePayableView view : purchaseQueryApi.findPurchasePayables(poIds)) {
            byId.put(view.id(), view.poNo());
        }
        return byId;
    }

    /** 原币 → CNY(rate 缺省 null → null,缺口计数纪律,禁猜禁取 1);金额 4 位小数 */
    private BigDecimal toCny(BigDecimal amount, BigDecimal rate) {
        return rate == null ? null : amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    private static long nvlPageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private static long nvlPageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 500);
    }
}
