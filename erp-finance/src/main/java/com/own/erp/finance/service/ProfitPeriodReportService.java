package com.own.erp.finance.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.finance.entity.ProfitPeriodReport;
import com.own.erp.finance.entity.SettlementReport;
import com.own.erp.finance.mapper.ProfitPeriodQueryMapper;
import com.own.erp.finance.mapper.ProfitPeriodReportMapper;
import com.own.erp.finance.mapper.SettlementReportMapper;
import com.own.erp.finance.profit.PeriodSettlementSide;
import com.own.erp.finance.profit.SettlementFeeSum;
import com.own.erp.finance.request.query.ProfitPeriodReportQuery;
import com.own.erp.finance.response.ProfitPeriodReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 周期利润报告服务(#19 三口径第二层,docs/03 §6.4):profit_period_report 域整域收口,
 *     Controller 不直连 Mapper(docs/07 §2.1)。系统写入表——对外仅只读;写入口 = rebuildForReport
 *     (结算报告 PARSED 后同事务触发 + ProfitPeriodJob 每日兜底)。
 *     双侧聚合:结算侧 ProfitPeriodQueryMapper.sumSettlementFees 分费种原币合计(报告原符号,
 *     按周期止 resolveRate 冻结折算 CNY,缺汇率 CNY 列 NULL 不静默);订单侧复用第一层
 *     ProfitQueryService.summarize 同周期窗重算(售价/实际+预估佣金/利润 CNY)。
 *     <p><b>校差算法(#32 拍板 2026-09-12,人工授权 AI 落地):①跨期系统性差(结算按 posted_at/
 *     订单按 order_time 落窗)不修补窗口,diff_remark 注明;②容差 0.01 CNY(同 RefundReconciliationService
 *     防尾差),任一超容差 diff_flag=1;③三态优先 RATE_MISSING(缺汇率 CNY 列全 NULL,diff 不可算);
 *     ④收入差 = 结算 SALE 行合计 − 订单收入(同口径对齐),TRANSFER 作打款事实存列不进差值,
 *     REFUND/ADVERTISING/OTHER 归 otherFee;⑤预估佣金不单列,缺口计数进 diff_remark。</b>
 *     幂等落库:uk(shop_id,settlement_id) 单语句 ODKU upsert(行别名 AS new,9.7.2 原生形态,
 *     重算整行覆盖不重复建行);created_at/updated_at 交库默认值与 ON UPDATE。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitPeriodReportService {

    /** 状态:勾稽平 */
    public static final String STATUS_OK = "OK";
    /** 状态:有差异(任一校差超容差) */
    public static final String STATUS_DIFF = "DIFF";
    /** 状态:缺汇率(CNY 列不可得,缺口纪律不静默) */
    public static final String STATUS_RATE_MISSING = "RATE_MISSING";

    /**
     * 周期订单侧全量行硬上限:复用第一层全量 SQL 的 LIMIT 20000 防御(单店单结算期约 14 天,远低于上限);
     * 量级增长随落库方案一并复核,删防御须留说明(docs/plans/19-profit-caliber.md 红线)
     */
    private static final int PERIOD_WINDOW_LINE_LIMIT = 20000;

    /** 校差容差 0.01 本位币(同 RefundReconciliationService.AMOUNT_TOLERANCE 防尾差,严格大于才标记) */
    private static final BigDecimal DIFF_TOLERANCE = new BigDecimal("0.01");

    private final ProfitPeriodReportMapper profitPeriodReportMapper;
    private final ProfitPeriodQueryMapper profitPeriodQueryMapper;
    private final SettlementReportMapper settlementReportMapper;
    private final ExchangeRateService exchangeRateService;
    private final ProfitQueryService profitQueryService;

    /** 分页查询(店铺/状态过滤;按周期止倒序,新报告在前) */
    public Page<ProfitPeriodReportResponse> page(ProfitPeriodReportQuery query) {
        Page<ProfitPeriodReport> result = profitPeriodReportMapper.selectPage(
                new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<ProfitPeriodReport>()
                        .eq(query.getShopId() != null, ProfitPeriodReport::getShopId, query.getShopId())
                        .eq(StrUtil.isNotBlank(query.getStatus()), ProfitPeriodReport::getStatus, query.getStatus())
                        .orderByDesc(ProfitPeriodReport::getPeriodEnd)
                        .orderByDesc(ProfitPeriodReport::getId));
        Page<ProfitPeriodReportResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(ProfitPeriodReportResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public ProfitPeriodReportResponse getById(Long id) {
        ProfitPeriodReport profitPeriodReport = profitPeriodReportMapper.selectById(id);
        return profitPeriodReport == null ? null : ProfitPeriodReportResponse.from(profitPeriodReport);
    }

    /**
     * 按结算报告重算/刷新一期周期行(幂等靠 uk(shop_id,settlement_id) ODKU upsert)。
     * FAILED 暂存态不生成(待重拉转 PARSED 后经 overwrite 路径补派生,同回款派生钩子口径)。
     * 事务语义:经 SettlementService PARSED 钩子调用时REQUIRED 加入落库事务(同事务原子,禁 AFTER_COMMIT);
     * Job 兜底路径单期隔离见 rebuildAllParsedReports。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebuildForReport(Long reportId) {
        SettlementReport report = settlementReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("结算报告不存在,无法生成周期利润: " + reportId);
        }
        if (!Objects.equals(SettlementService.STATUS_PARSED, report.getStatus())) {
            log.info("结算报告非 PARSED,跳过周期利润生成 report={} status={}", reportId, report.getStatus());
            return;
        }
        // 汇率快照:按周期止回溯(锚点拍板 docs/03 §6.4;CNY 短路 1,无报价 null 禁猜)
        BigDecimal rateUsed = exchangeRateService.resolveRate(report.getCurrency(), report.getPeriodEnd());
        // 结算侧:分费种原币合计 → 折 CNY(缺汇率 CNY 列全 NULL,rate_missing=1)
        List<SettlementFeeSum> feeSums = profitPeriodQueryMapper.sumSettlementFees(reportId);
        PeriodSettlementSide settlementSide = PeriodSettlementSide.from(feeSums, rateUsed);
        // 订单侧:同周期窗 [periodStart, periodEnd) 复用第一层归集管线(已支付三态,order_time 落窗)
        OrderProfitSummary orderSide = profitQueryService.summarize(new OrderProfitQuery(
                report.getShopId(), null, null, report.getPeriodStart(), report.getPeriodEnd(),
                1, PERIOD_WINDOW_LINE_LIMIT, null));

        ProfitPeriodReport row = calibrate(report, rateUsed, settlementSide, orderSide);
        profitPeriodReportMapper.upsertPeriod(row);
        log.info("周期利润重算落库 report={} shop={} status={} diffFlag={}",
                reportId, report.getShopId(), row.getStatus(), row.getDiffFlag());
    }

    /**
     * Job 兜底入口:全量 PARSED 报告逐期重算(报告 14 天一份量级小,不进 SQL 日期轴)。
     * 不加类级事务:单期唯一写 = 单语句 ODKU upsert 自原子;逐期 try-catch 隔离,单期失败不回滚全批。
     */
    public void rebuildAllParsedReports() {
        List<SettlementReport> reports = settlementReportMapper.selectList(
                new LambdaQueryWrapper<SettlementReport>()
                        .eq(SettlementReport::getStatus, SettlementService.STATUS_PARSED)
                        .orderByDesc(SettlementReport::getId));
        for (SettlementReport report : reports) {
            try {
                rebuildForReport(report.getId());
            } catch (Exception e) {
                log.error("周期利润单期重算失败,隔离继续 report={}", report.getId(), e);
            }
        }
    }

    /**
     * 周期校差与三态状态机(#32 拍板 2026-09-12)。
     * 输入双侧聚合(均已折 CNY,带符号口径同结算报告:收入正/费用负):
     * <ul>
     *   <li>settlementSide:SALE 订单侧收入流 / TRANSFER 回款 / COMMISSION 佣金 / FBA_FEE+STORAGE FBA 系 /
     *       其余费种(REFUND/ADVERTISING/OTHER)归 otherFee(null=该费种无行,0=有行合计为零),缺汇率时五列全 null;</li>
     *   <li>orderSide:周期窗订单行售价合计 salesCny、佣金合计 commissionCny(实际+预估,
     *       commissionMissingCount 仍按缺实际计数)、利润合计 profitCny,三个缺口计数不静默归零。</li>
     * </ul>
     * 口径:收入差 = settleSales − orderIncome、佣金差 = settleCommission − orderCommission(同号相减,
     * 正=结算侧多);结算侧缺 SALE/COMMISSION 行时对应差不可比置 null 不硬算;RATE_MISSING 优先
     * (rateUsed==null → CNY 列全 NULL 只落周期身份列与缺口计数)。
     */
    private ProfitPeriodReport calibrate(SettlementReport report, BigDecimal rateUsed,
                                         PeriodSettlementSide settlementSide, OrderProfitSummary orderSide) {
        String gapRemark = gapRemark(orderSide);
        if (rateUsed == null) {
            // 缺汇率:禁猜禁折,CNY 列全 NULL,status=RATE_MISSING(三态优先,#32 拍板③)
            return baseBuilder(report)
                    .rateUsed(null)
                    .rateMissing(1)
                    .diffFlag(0)
                    .diffRemark(gapRemark)
                    .status(STATUS_RATE_MISSING)
                    .build();
        }
        BigDecimal diffIncome = settlementSide.settleSales() == null ? null
                : settlementSide.settleSales().subtract(orderSide.salesCny());
        BigDecimal diffCommission = settlementSide.settleCommission() == null ? null
                : settlementSide.settleCommission().subtract(orderSide.commissionCny());
        boolean incomeDiff = diffIncome != null && diffIncome.abs().compareTo(DIFF_TOLERANCE) > 0;
        boolean commissionDiff = diffCommission != null && diffCommission.abs().compareTo(DIFF_TOLERANCE) > 0;
        boolean flagged = incomeDiff || commissionDiff;
        String remark = flagged ? diffRemark(settlementSide, orderSide, diffIncome, diffCommission) : null;
        if (gapRemark != null) {
            remark = remark == null ? gapRemark : remark + ";" + gapRemark;
        }
        return baseBuilder(report)
                .rateUsed(rateUsed)
                .rateMissing(0)
                .orderIncome(orderSide.salesCny())
                .settleIncome(settlementSide.settleIncome())
                .settleCommission(settlementSide.settleCommission())
                .fbaFee(settlementSide.fbaFee())
                .otherFee(settlementSide.otherFee())
                .orderCommission(orderSide.commissionCny())
                .orderProfit(orderSide.profitCny())
                .diffIncome(diffIncome)
                .diffCommission(diffCommission)
                .diffFlag(flagged ? 1 : 0)
                .diffRemark(remark)
                .status(flagged ? STATUS_DIFF : STATUS_OK)
                .build();
    }

    /** 周期身份列公共段(与汇率/金额无关,三态共用;rateUsed 由各态自行回填,防漏配) */
    private ProfitPeriodReport.ProfitPeriodReportBuilder baseBuilder(SettlementReport report) {
        return ProfitPeriodReport.builder()
                .shopId(report.getShopId())
                .settlementId(report.getId())
                .periodStart(report.getPeriodStart())
                .periodEnd(report.getPeriodEnd())
                .currency(report.getCurrency());
    }

    /** 缺口计数段(#32 拍板②:差异计数不静默归零,非零才写不拼空段) */
    private String gapRemark(OrderProfitSummary orderSide) {
        if (orderSide.missingRateCount() == 0 && orderSide.costMissingCount() == 0
                && orderSide.commissionMissingCount() == 0) {
            return null;
        }
        return "缺口:缺汇率" + orderSide.missingRateCount()
                + "/未出库" + orderSide.costMissingCount()
                + "/缺佣金" + orderSide.commissionMissingCount()
                + "(行" + orderSide.orderItemCount() + ")";
    }

    /** 超容差差异段(#32 拍板①:跨期系统性差注明不吞差;SALE 合计随收入差留痕供复核) */
    private String diffRemark(PeriodSettlementSide side, OrderProfitSummary orderSide,
                              BigDecimal diffIncome, BigDecimal diffCommission) {
        List<String> parts = new ArrayList<>(3);
        parts.add(diffIncome == null ? "收入差不可比(结算无SALE行)"
                : "收入差=" + diffIncome + "(SALE " + side.settleSales() + "-订单 " + orderSide.salesCny() + ")");
        parts.add(diffCommission == null ? "佣金差不可比(结算无COMMISSION行)"
                : "佣金差=" + diffCommission + "(结算 " + side.settleCommission() + "-订单 " + orderSide.commissionCny() + ")");
        parts.add("跨期:结算按posted_at/订单按order_time");
        return String.join(";", parts);
    }
}
