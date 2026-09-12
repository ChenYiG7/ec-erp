package com.own.erp.finance.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.GoodsQueryApi.SkuView;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.PurchaseQueryApi.SkuSupplierView;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.contract.WarehouseApi.WarehouseView;
import com.own.erp.finance.constant.FirstLegConsts;
import com.own.erp.finance.entity.FirstLegAlloc;
import com.own.erp.finance.entity.FirstLegBox;
import com.own.erp.finance.entity.FirstLegBoxItem;
import com.own.erp.finance.entity.FirstLegShipment;
import com.own.erp.finance.mapper.FirstLegAllocMapper;
import com.own.erp.finance.mapper.FirstLegBoxItemMapper;
import com.own.erp.finance.mapper.FirstLegBoxMapper;
import com.own.erp.finance.mapper.FirstLegQueryMapper;
import com.own.erp.finance.mapper.FirstLegShipmentMapper;
import com.own.erp.finance.request.command.FirstLegShipRequest;
import com.own.erp.finance.request.command.FirstLegShipmentSaveRequest;
import com.own.erp.finance.request.command.FirstLegShipmentSaveRequest.BoxItemSave;
import com.own.erp.finance.request.command.FirstLegShipmentSaveRequest.BoxSave;
import com.own.erp.finance.request.query.FirstLegShipmentQuery;
import com.own.erp.finance.response.FirstLegAllocResponse;
import com.own.erp.finance.response.FirstLegBoxItemResponse;
import com.own.erp.finance.response.FirstLegBoxResponse;
import com.own.erp.finance.response.FirstLegShipmentResponse;
import com.own.erp.finance.response.FirstLegSkuAllocRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程运费分摊服务(#33,docs/plans/first-mile-freight.md):first_leg_shipment(+箱/箱内件/分摊结果)
 *     域整域收口,Controller 不直连 Mapper(docs/07 §2.1),entity 不出本层。
 *     路线拍板 B(期间费用):运费分摊落 first_leg_alloc 作为利润第三层费用行,不触碰移动加权成本账;
 *     本期 SHIPPED 不联动跨仓动账(海外仓/FBA 库存 track 随 fba-shipment 拍板)。
 *     状态机 DRAFT→BOXED→SHIPPED→ALLOCATED→CLOSED(CANCELED 旁路仅 DRAFT/BOXED),流转一律条件更新
 *     (WHERE 即守卫,docs/07 §6.3);单据域物理删除(仅 DRAFT/CANCELED,docs/07 §6.4)。
 *     三策略分摊(数量/重量/金额)+ 全0分母降级按数量(留 remark 不静默)+ 部分缺基数拦截;
 *     Σalloc 结构性等于 freight_cny(尾差并入最大基数行)。跨域只走契约,禁横向依赖(铁律 2)
 */
@Slf4j
@Service
public class FirstLegShipmentService {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SEQ_RETRY_LIMIT = 5;
    private static final int AMOUNT_SCALE = 4;

    private final FirstLegShipmentMapper shipmentMapper;
    private final FirstLegBoxMapper boxMapper;
    private final FirstLegBoxItemMapper boxItemMapper;
    private final FirstLegAllocMapper allocMapper;
    private final FirstLegQueryMapper queryMapper;
    private final ExchangeRateService exchangeRateService;
    private final WarehouseApi warehouseApi;
    private final GoodsQueryApi goodsQueryApi;
    private final PurchaseQueryApi purchaseQueryApi;
    private final CurrentUserApi currentUserApi;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service,急切装配成环(docs/07 §2.2) */
    public FirstLegShipmentService(FirstLegShipmentMapper shipmentMapper,
                                   FirstLegBoxMapper boxMapper,
                                   FirstLegBoxItemMapper boxItemMapper,
                                   FirstLegAllocMapper allocMapper,
                                   FirstLegQueryMapper queryMapper,
                                   ExchangeRateService exchangeRateService,
                                   @Lazy WarehouseApi warehouseApi,
                                   @Lazy GoodsQueryApi goodsQueryApi,
                                   @Lazy PurchaseQueryApi purchaseQueryApi,
                                   @Lazy CurrentUserApi currentUserApi) {
        this.shipmentMapper = shipmentMapper;
        this.boxMapper = boxMapper;
        this.boxItemMapper = boxItemMapper;
        this.allocMapper = allocMapper;
        this.queryMapper = queryMapper;
        this.exchangeRateService = exchangeRateService;
        this.warehouseApi = warehouseApi;
        this.goodsQueryApi = goodsQueryApi;
        this.purchaseQueryApi = purchaseQueryApi;
        this.currentUserApi = currentUserApi;
    }

    // ============================ 查询面 ============================

    /** 分页(双仓名 XML 联表投影,不带装箱/分摊) */
    public Page<FirstLegShipmentResponse> page(FirstLegShipmentQuery query) {
        return queryMapper.pageRows(new Page<>(query.getPageNo(), query.pageSize()), query);
    }

    /** 详情:主单 + 装箱树(箱+内件)+ 分摊行;skuCode 经契约一次批量回填;不存在返回 null */
    public FirstLegShipmentResponse getById(Long id) {
        FirstLegShipment shipment = shipmentMapper.selectById(id);
        if (shipment == null) {
            return null;
        }
        List<FirstLegBox> boxes = listBoxes(id);
        List<FirstLegBoxItem> items = listItemsOfBoxes(boxes);
        List<FirstLegAlloc> allocs = allocMapper.selectList(new LambdaQueryWrapper<FirstLegAlloc>()
                .eq(FirstLegAlloc::getShipmentId, id)
                .orderByAsc(FirstLegAlloc::getSkuId));

        Set<Long> skuIds = new LinkedHashSet<>();
        items.forEach(i -> skuIds.add(i.getSkuId()));
        allocs.forEach(a -> skuIds.add(a.getSkuId()));
        Map<Long, String> skuCodeById = loadSkuCodeMap(skuIds);

        Map<Long, List<FirstLegBoxItemResponse>> itemResponsesByBox = new HashMap<>();
        for (FirstLegBoxItem item : items) {
            itemResponsesByBox.computeIfAbsent(item.getBoxId(), k -> new ArrayList<>())
                    .add(FirstLegBoxItemResponse.builder()
                            .id(item.getId())
                            .boxId(item.getBoxId())
                            .skuId(item.getSkuId())
                            .skuCode(skuCodeById.get(item.getSkuId()))
                            .quantity(item.getQuantity())
                            .build());
        }
        List<FirstLegBoxResponse> boxResponses = boxes.stream()
                .map(b -> FirstLegBoxResponse.from(b).withItems(
                        itemResponsesByBox.getOrDefault(b.getId(), List.of())))
                .toList();
        List<FirstLegAllocResponse> allocResponses = allocs.stream()
                .map(a -> FirstLegAllocResponse.builder()
                        .id(a.getId()).shipmentId(a.getShipmentId()).skuId(a.getSkuId())
                        .skuCode(skuCodeById.get(a.getSkuId()))
                        .allocAmount(a.getAllocAmount()).allocBase(a.getAllocBase()).strategy(a.getStrategy())
                        .build())
                .toList();
        return FirstLegShipmentResponse.from(shipment)
                .withBoxes(boxResponses)
                .withAllocs(allocResponses);
    }

    /** SKU 维度头程费用汇总(Σ ALLOCATED/CLOSED 分摊;利润第三层聚合源) */
    public List<FirstLegSkuAllocRow> listSkuAllocSummary(Long skuId, LocalDateTime shippedFrom, LocalDateTime shippedTo) {
        return queryMapper.listSkuAllocSummary(skuId, shippedFrom, shippedTo);
    }

    // ============================ 写侧:建单/改单/删除(仅 DRAFT) ============================

    /** 建单(DRAFT):流向校验(SELF→OVERSEAS/FBA)+ 装箱校验 → 生成 FL 单号落主单 → 落箱/内件(同事务) */
    @Transactional(rollbackFor = Exception.class)
    public Long save(FirstLegShipmentSaveRequest request) {
        String strategy = normalizeStrategy(request.allocateStrategy());
        validateFlow(request.fromWarehouseId(), request.toWarehouseId());
        validateBoxes(request.boxes());
        FirstLegShipment shipment = FirstLegShipment.builder()
                .fromWarehouseId(request.fromWarehouseId())
                .toWarehouseId(request.toWarehouseId())
                .allocateStrategy(strategy)
                .currency(ExchangeRateService.BASE_CURRENCY)
                .status(FirstLegConsts.STATUS_DRAFT)
                .remark(StrUtil.trimToNull(request.remark()))
                .createdBy(currentUserApi.currentUserId())
                .build();
        Long id = insertWithGeneratedNo(shipment);
        replaceBoxes(id, request.boxes());
        log.info("头程发货单建单成功 shipmentId={} no={} 箱数={}", id, shipment.getShipmentNo(),
                request.boxes() == null ? 0 : request.boxes().size());
        return id;
    }

    /**
     * 改单:仅 DRAFT(行锁读串行化与装箱/发货竞态);表头更新 + 装箱整体替换(先删后插)。
     * BOXED 起箱内容冻结,改单走取消重建
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FirstLegShipmentSaveRequest request) {
        FirstLegShipment exist = shipmentMapper.selectByIdForUpdate(id);
        if (exist == null) {
            throw new BusinessException("头程发货单不存在:" + id);
        }
        if (!FirstLegConsts.STATUS_DRAFT.equals(exist.getStatus())) {
            throw new BusinessException("仅草稿状态可修改,当前状态:" + exist.getStatus());
        }
        String strategy = normalizeStrategy(request.allocateStrategy());
        validateFlow(request.fromWarehouseId(), request.toWarehouseId());
        validateBoxes(request.boxes());
        FirstLegShipment shipment = FirstLegShipment.builder()
                .id(id)
                .fromWarehouseId(request.fromWarehouseId())
                .toWarehouseId(request.toWarehouseId())
                .allocateStrategy(strategy)
                .remark(StrUtil.trimToNull(request.remark()))
                .build();
        shipmentMapper.updateById(shipment);
        deleteBoxes(id);
        replaceBoxes(id, request.boxes());
        log.info("头程发货单改单成功 shipmentId={}", id);
    }

    /** 删除:仅 DRAFT/CANCELED(单据域物理删除,docs/07 §6.4);SHIPPED 起运费/分摊为财务事实禁删 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FirstLegShipment exist = shipmentMapper.selectById(id);
        if (exist == null) {
            return;
        }
        String status = exist.getStatus();
        if (!FirstLegConsts.STATUS_DRAFT.equals(status) && !FirstLegConsts.STATUS_CANCELED.equals(status)) {
            throw new BusinessException("已装箱/已发货/已分摊的头程单禁止删除(财务事实需可追溯),当前状态:" + status);
        }
        deleteBoxes(id);
        shipmentMapper.deleteById(id);
    }

    // ============================ 写侧:状态机动作 ============================

    /** 装箱完成:DRAFT→BOXED 条件更新守卫;至少 1 箱且有内件,否则随事务回滚 */
    @Transactional(rollbackFor = Exception.class)
    public void box(Long id) {
        if (shipmentMapper.casBox(id) == 0) {
            throw new BusinessException("装箱失败:头程单不存在或不是草稿状态:" + id);
        }
        List<FirstLegBox> boxes = listBoxes(id);
        int itemRows = listItemsOfBoxes(boxes).size();
        if (CollUtil.isEmpty(boxes) || itemRows == 0) {
            throw new BusinessException("装箱失败:至少需要 1 个箱且箱内有 SKU,请先录入装箱明细:" + id);
        }
        log.info("头程发货单装箱完成 shipmentId={} 箱数={}", id, boxes.size());
    }

    /**
     * 确认发货(BOXED→SHIPPED,复合事务):cas 守卫占位 → 录运费并冻结汇率 → 回填落库。
     * 汇率口径:手填 exchangeRate 优先(必须>0);空按 shippedAt 回溯 resolveRate;
     * 非 CNY 无报价直接拦截(禁猜,验收红线),补汇率快照或手填后重试
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id, FirstLegShipRequest request) {
        if (shipmentMapper.casShip(id) == 0) {
            throw new BusinessException("发货失败:头程单不存在或不是已装箱状态:" + id);
        }
        FirstLegShipment shipment = shipmentMapper.selectById(id);
        LocalDateTime shippedAt = request.shippedAt() == null
                ? LocalDateTime.now(PullConsts.ZONE) : request.shippedAt();
        String currency = StrUtil.blankToDefault(StrUtil.trimToNull(request.currency()),
                ExchangeRateService.BASE_CURRENCY);
        BigDecimal rate;
        if (ExchangeRateService.BASE_CURRENCY.equalsIgnoreCase(currency)) {
            // 本位币短路 1(同 ExchangeRateService.resolveRate 口径,Service 显式表达避免多余查表)
            rate = BigDecimal.ONE;
        } else if (request.exchangeRate() != null) {
            if (request.exchangeRate().signum() <= 0) {
                throw new BusinessException("手填汇率必须大于0");
            }
            rate = request.exchangeRate();
        } else {
            rate = exchangeRateService.resolveRate(currency, shippedAt);
        }
        if (rate == null) {
            throw new BusinessException("币种 " + currency + " 在发货日 " + shippedAt.toLocalDate()
                    + " 无汇率报价,请先维护汇率快照或手填汇率(禁猜汇率)");
        }
        BigDecimal freightCny = request.freightAmount().multiply(rate)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        shipment.setCarrier(StrUtil.trimToNull(request.carrier()));
        shipment.setWaybillNo(StrUtil.trimToNull(request.waybillNo()));
        shipment.setChargeWeight(request.chargeWeight());
        shipment.setVolumeWeight(request.volumeWeight());
        shipment.setFreightAmount(request.freightAmount());
        shipment.setCurrency(currency);
        shipment.setExchangeRate(rate);
        shipment.setFreightCny(freightCny);
        shipment.setShippedAt(shippedAt);
        shipment.setStatus(FirstLegConsts.STATUS_SHIPPED);
        shipmentMapper.updateById(shipment);
        log.info("头程发货单确认发货 shipmentId={} freight={} {} rate={} cny={}",
                id, request.freightAmount(), currency, rate, freightCny);
    }

    /**
     * 运费分摊(SHIPPED→ALLOCATED,核心复合事务):cas 守卫(ALLOCATED 脱靶即拦重算,防利润口径漂移)
     * → 跨箱汇总各 SKU 件数 → 按主单策略算基数(WEIGHT=qty×weight_g;AMOUNT=qty×最近采购价回退cost_price;
     * QTY=件数)→ 全0分母降级 QTY 并写 alloc_remark;部分 SKU 缺基数拦截不静默
     * → 按比例摊 freight_cny,尾差并入最大基数行(Σ 结构性=运费,容差 0.01)→ 落 first_leg_alloc。
     * 分摊一旦确认不可重算覆盖,重算=作废重开
     */
    @Transactional(rollbackFor = Exception.class)
    public void allocate(Long id) {
        if (shipmentMapper.casAllocate(id) == 0) {
            throw new BusinessException("分摊失败:头程单不存在或不是已发货状态(已分摊单不可重算):" + id);
        }
        FirstLegShipment shipment = shipmentMapper.selectById(id);
        Map<Long, Integer> qtyBySku = aggregateQuantities(id);
        if (qtyBySku.isEmpty()) {
            throw new BusinessException("分摊失败:装箱无 SKU 明细:" + id);
        }
        if (shipment.getFreightCny() == null
                || shipment.getFreightCny().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("分摊失败:运费本位币缺失或非正(发货环节异常),请核查:" + id);
        }

        String wanted = shipment.getAllocateStrategy();
        Map<Long, BigDecimal> computedBases = switch (wanted) {
            case FirstLegConsts.STRATEGY_QTY -> qtyBases(qtyBySku);
            case FirstLegConsts.STRATEGY_WEIGHT -> weightBases(qtyBySku);
            case FirstLegConsts.STRATEGY_AMOUNT -> amountBases(qtyBySku);
            default -> throw new BusinessException("分摊策略非法(QTY/WEIGHT/AMOUNT):" + wanted);
        };
        String effective = wanted;
        String remark = null;
        BigDecimal totalBase = computedBases.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!FirstLegConsts.STRATEGY_QTY.equals(wanted) && totalBase.compareTo(BigDecimal.ZERO) == 0) {
            // 分母全 0 兜底:降级按数量,留痕不静默
            effective = FirstLegConsts.STRATEGY_QTY;
            computedBases = qtyBases(qtyBySku);
            remark = FirstLegConsts.STRATEGY_WEIGHT.equals(wanted)
                    ? "各 SKU 重量(weight_g)基数全为 0/未维护,降级按数量分摊"
                    : "各 SKU 金额基数全为 0(无最近采购价且无成本价),降级按数量分摊";
        }
        // effectively-final 副本供 lambda/分摊使用(computedBases 在降级分支被重赋过)
        final Map<Long, BigDecimal> baseBySku = computedBases;
        if (hasPartialZero(qtyBySku, baseBySku)) {
            List<Long> missing = qtyBySku.keySet().stream()
                    .filter(skuId -> baseBySku.getOrDefault(skuId, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) == 0)
                    .sorted().toList();
            throw new BusinessException("分摊失败:部分 SKU "
                    + (FirstLegConsts.STRATEGY_WEIGHT.equals(wanted) ? "重量(weight_g)未维护" : "采购价/成本价缺失")
                    + ",基数为 0(不静默摊 0),请补齐主数据或改按数量策略,SKU=" + missing);
        }

        Map<Long, BigDecimal> amountBySku = apportion(shipment.getFreightCny(), baseBySku);
        LocalDateTime now = LocalDateTime.now(PullConsts.ZONE);
        for (Long skuId : amountBySku.keySet().stream().sorted().toList()) {
            allocMapper.insert(FirstLegAlloc.builder()
                    .shipmentId(id)
                    .skuId(skuId)
                    .allocAmount(amountBySku.get(skuId))
                    .allocBase(baseBySku.get(skuId).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP))
                    .strategy(effective)
                    .build());
        }
        shipment.setAllocRemark(remark);
        shipment.setAllocatedAt(now);
        shipment.setStatus(FirstLegConsts.STATUS_ALLOCATED);
        shipmentMapper.updateById(shipment);
        log.info("头程运费分摊完成 shipmentId={} strategy={}→{} sku={} freightCny={} remark={}",
                id, wanted, effective, amountBySku.size(), shipment.getFreightCny(), remark);
    }

    /** 关闭:ALLOCATED→CLOSED 条件更新守卫 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        if (shipmentMapper.casClose(id) == 0) {
            throw new BusinessException("关闭失败:头程单不存在或不是已分摊状态:" + id);
        }
        log.info("头程发货单关闭 shipmentId={}", id);
    }

    /** 取消:DRAFT/BOXED→CANCELED(SHIPPED 起禁取消,逆向流程后续拍板) */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        if (shipmentMapper.casCancel(id) == 0) {
            throw new BusinessException("取消失败:仅草稿/已装箱状态可取消(已发货起运费已录不可取消):" + id);
        }
        log.info("头程发货单取消 shipmentId={}", id);
    }

    // ============================ 内部分摊 ============================

    /** QTY 基数:跨箱件数(恒正) */
    private Map<Long, BigDecimal> qtyBases(Map<Long, Integer> qtyBySku) {
        Map<Long, BigDecimal> bases = new HashMap<>();
        qtyBySku.forEach((skuId, qty) -> bases.put(skuId, BigDecimal.valueOf(qty)));
        return bases;
    }

    /** WEIGHT 基数:qty × product_sku.weight_g(g,整数);未维护/非正重量按 0(全0降级/部分0拦截由调用方收口) */
    private Map<Long, BigDecimal> weightBases(Map<Long, Integer> qtyBySku) {
        Map<Long, SkuView> skuById = loadSkuMap(qtyBySku.keySet());
        Map<Long, BigDecimal> bases = new HashMap<>();
        qtyBySku.forEach((skuId, qty) -> {
            SkuView sku = skuById.get(skuId);
            Integer weightG = sku == null ? null : sku.weightG();
            BigDecimal base = (weightG != null && weightG > 0)
                    ? BigDecimal.valueOf(qty.longValue() * weightG.longValue())
                    : BigDecimal.ZERO;
            bases.put(skuId, base);
        });
        return bases;
    }

    /**
     * AMOUNT 基数:qty × 单价(CNY)。单价取最近采购价(PurchaseQueryApi,非草稿采购最新一行),
     * 缺/非正回退 product_sku.cost_price;两者皆无按 0(全0降级/部分0拦截由调用方收口)
     */
    private Map<Long, BigDecimal> amountBases(Map<Long, Integer> qtyBySku) {
        Map<Long, SkuView> skuById = loadSkuMap(qtyBySku.keySet());
        Map<Long, SkuSupplierView> latestById = new HashMap<>();
        for (SkuSupplierView view : purchaseQueryApi.findLatestSupplierBySkuIds(qtyBySku.keySet())) {
            latestById.put(view.skuId(), view);
        }
        Map<Long, BigDecimal> bases = new HashMap<>();
        qtyBySku.forEach((skuId, qty) -> {
            BigDecimal unit = null;
            SkuSupplierView latest = latestById.get(skuId);
            if (latest != null && latest.lastPrice() != null && latest.lastPrice().signum() > 0) {
                unit = latest.lastPrice();
            } else {
                SkuView sku = skuById.get(skuId);
                if (sku != null && sku.costPrice() != null && sku.costPrice().signum() > 0) {
                    unit = sku.costPrice();
                }
            }
            BigDecimal base = unit == null ? BigDecimal.ZERO
                    : BigDecimal.valueOf(qty.longValue()).multiply(unit).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            bases.put(skuId, base);
        });
        return bases;
    }

    /**
     * 比例分摊:各行 share = freightCny × base/totalBase(HALF_UP 4 位);
     * 尾差(各行舍入残差累计)并入基数最大行(并列取最小 skuId),保证 Σalloc 结构性 == freightCny
     */
    private Map<Long, BigDecimal> apportion(BigDecimal freightCny, Map<Long, BigDecimal> baseBySku) {
        // 显式排序决定尾差行(不受 HashMap 遍历顺序影响):基数降序,并列取最小 skuId
        List<Long> skuIds = new ArrayList<>(baseBySku.keySet());
        skuIds.sort((a, b) -> {
            int cmp = baseBySku.get(b).compareTo(baseBySku.get(a));
            return cmp != 0 ? cmp : Long.compare(a, b);
        });
        Long remainderSku = skuIds.isEmpty()
                ? null : skuIds.get(0);
        if (remainderSku == null) {
            throw new BusinessException("分摊失败:无分摊 SKU");
        }
        BigDecimal totalBase = baseBySku.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, BigDecimal> amounts = new HashMap<>();
        BigDecimal distributed = BigDecimal.ZERO;
        for (Long skuId : skuIds) {
            if (skuId.equals(remainderSku)) {
                continue;
            }
            BigDecimal share = freightCny.multiply(baseBySku.get(skuId))
                    .divide(totalBase, AMOUNT_SCALE, RoundingMode.HALF_UP);
            amounts.put(skuId, share);
            distributed = distributed.add(share);
        }
        BigDecimal remainder = freightCny.subtract(distributed);
        if (remainder.signum() < 0) {
            // 正基数下数学上不可能,防御性封死(防半成品分摊行落库)
            throw new BusinessException("分摊失败:尾差行为负,基数/金额异常,禁落库");
        }
        amounts.put(remainderSku, remainder);
        return amounts;
    }

    private boolean hasPartialZero(Map<Long, Integer> qtyBySku, Map<Long, BigDecimal> baseBySku) {
        boolean anyPositive = false;
        boolean anyZero = false;
        for (Long skuId : qtyBySku.keySet()) {
            if (baseBySku.getOrDefault(skuId, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) == 0) {
                anyZero = true;
            } else {
                anyPositive = true;
            }
        }
        return anyPositive && anyZero;
    }

    // ============================ 内部装配/校验 ============================

    /** 跨箱汇总每 SKU 总件数(同 SKU 可分布在多箱;箱内 uk 已保证不重复) */
    private Map<Long, Integer> aggregateQuantities(Long shipmentId) {
        List<FirstLegBox> boxes = listBoxes(shipmentId);
        Map<Long, Integer> qtyBySku = new HashMap<>();
        for (FirstLegBox box : boxes) {
            List<FirstLegBoxItem> items = boxItemMapper.selectList(new LambdaQueryWrapper<FirstLegBoxItem>()
                    .eq(FirstLegBoxItem::getBoxId, box.getId()));
            for (FirstLegBoxItem item : items) {
                qtyBySku.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
            }
        }
        return qtyBySku;
    }

    /** 流向校验:发货仓必须 SELF 国内仓,目的仓必须 OVERSEAS/FBA(仓型经契约取数,铁律 2) */
    private void validateFlow(Long fromWarehouseId, Long toWarehouseId) {
        WarehouseView from = warehouseApi.findWarehouseViewById(fromWarehouseId);
        if (from == null) {
            throw new BusinessException("国内发货仓不存在:" + fromWarehouseId);
        }
        if (!FirstLegConsts.WH_TYPE_SELF.equals(from.whType())) {
            throw new BusinessException("头程发货仓必须是国内自仓(SELF),当前为 " + from.whType()
                    + ":" + from.whName());
        }
        WarehouseView to = warehouseApi.findWarehouseViewById(toWarehouseId);
        if (to == null) {
            throw new BusinessException("目的仓不存在:" + toWarehouseId);
        }
        if (!FirstLegConsts.WH_TYPE_OVERSEAS.equals(to.whType())
                && !FirstLegConsts.WH_TYPE_FBA.equals(to.whType())) {
            throw new BusinessException("头程目的仓必须是海外仓(OVERSEAS)或FBA仓,当前为 " + to.whType()
                    + ":" + to.whName());
        }
    }

    /** 装箱校验:箱号单内唯一;箱内件数>0、同箱 SKU 不重复;全部 SKU 经契约存在性校验(草稿允许空箱单) */
    private void validateBoxes(List<BoxSave> boxes) {
        if (CollUtil.isEmpty(boxes)) {
            return;
        }
        Set<String> boxNos = new HashSet<>();
        Set<Long> allSkuIds = new LinkedHashSet<>();
        for (BoxSave box : boxes) {
            String boxNo = StrUtil.trimToNull(box.boxNo());
            if (boxNo == null) {
                throw new BusinessException("箱号不能为空");
            }
            if (!boxNos.add(boxNo)) {
                throw new BusinessException("箱号在单内重复,请合并或改号:" + boxNo);
            }
            if (box.weight() != null && box.weight().signum() < 0) {
                throw new BusinessException("箱重不能为负:" + boxNo);
            }
            if (CollUtil.isEmpty(box.items())) {
                throw new BusinessException("箱内件不能为空,箱号:" + boxNo);
            }
            Set<Long> boxSkuIds = new HashSet<>();
            for (BoxItemSave item : box.items()) {
                if (item.skuId() == null || item.quantity() == null || item.quantity() <= 0) {
                    throw new BusinessException("箱内件非法(箱号 " + boxNo + "):SKU 必填且件数大于0");
                }
                if (!boxSkuIds.add(item.skuId())) {
                    throw new BusinessException("同一箱内同一 SKU 重复,请合并为一行(箱号 " + boxNo
                            + "):SKU=" + item.skuId());
                }
                allSkuIds.add(item.skuId());
            }
        }
        Map<Long, SkuView> skuById = loadSkuMap(allSkuIds);
        List<Long> missing = allSkuIds.stream().filter(id -> !skuById.containsKey(id)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("箱内件 SKU 不存在:" + missing);
        }
    }

    /** 落箱与箱内件(校验已过,builder 纯构造装配) */
    private void replaceBoxes(Long shipmentId, List<BoxSave> boxes) {
        if (CollUtil.isEmpty(boxes)) {
            return;
        }
        for (BoxSave boxReq : boxes) {
            FirstLegBox box = FirstLegBox.builder()
                    .shipmentId(shipmentId)
                    .boxNo(StrUtil.trim(boxReq.boxNo()))
                    .weight(boxReq.weight())
                    .lengthCm(boxReq.lengthCm())
                    .widthCm(boxReq.widthCm())
                    .heightCm(boxReq.heightCm())
                    .build();
            boxMapper.insert(box);
            for (BoxItemSave itemReq : boxReq.items()) {
                boxItemMapper.insert(FirstLegBoxItem.builder()
                        .boxId(box.getId())
                        .skuId(itemReq.skuId())
                        .quantity(itemReq.quantity())
                        .build());
            }
        }
    }

    /** 删除单的全部箱内件 + 箱(逐箱 eq 删除件,规避 LambdaWrapper.in() 纯单测急切解析坑,docs/07 §10) */
    private void deleteBoxes(Long shipmentId) {
        List<FirstLegBox> boxes = listBoxes(shipmentId);
        for (FirstLegBox box : boxes) {
            boxItemMapper.delete(new LambdaQueryWrapper<FirstLegBoxItem>()
                    .eq(FirstLegBoxItem::getBoxId, box.getId()));
        }
        boxMapper.delete(new LambdaQueryWrapper<FirstLegBox>()
                .eq(FirstLegBox::getShipmentId, shipmentId));
    }

    private List<FirstLegBox> listBoxes(Long shipmentId) {
        return boxMapper.selectList(new LambdaQueryWrapper<FirstLegBox>()
                .eq(FirstLegBox::getShipmentId, shipmentId)
                .orderByAsc(FirstLegBox::getId));
    }

    private List<FirstLegBoxItem> listItemsOfBoxes(List<FirstLegBox> boxes) {
        List<FirstLegBoxItem> items = new ArrayList<>();
        for (FirstLegBox box : boxes) {
            items.addAll(boxItemMapper.selectList(new LambdaQueryWrapper<FirstLegBoxItem>()
                    .eq(FirstLegBoxItem::getBoxId, box.getId())
                    .orderByAsc(FirstLegBoxItem::getId)));
        }
        return items;
    }

    /** 契约批量取 SKU 视图(存在性 + 重量/成本价) */
    private Map<Long, SkuView> loadSkuMap(Set<Long> skuIds) {
        Map<Long, SkuView> byId = new HashMap<>();
        if (CollUtil.isEmpty(skuIds)) {
            return byId;
        }
        for (SkuView view : goodsQueryApi.findSkusByIds(skuIds)) {
            byId.put(view.id(), view);
        }
        return byId;
    }

    /** 契约批量取 skuId→skuCode 映射(详情回填) */
    private Map<Long, String> loadSkuCodeMap(Set<Long> skuIds) {
        Map<Long, String> byCode = new HashMap<>();
        if (CollUtil.isEmpty(skuIds)) {
            return byCode;
        }
        for (SkuView view : goodsQueryApi.findSkusByIds(skuIds)) {
            byCode.put(view.id(), view.skuCode());
        }
        return byCode;
    }

    private String normalizeStrategy(String raw) {
        String strategy = StrUtil.trimToNull(raw);
        if (strategy == null) {
            return FirstLegConsts.STRATEGY_WEIGHT;
        }
        if (!FirstLegConsts.STRATEGY_QTY.equals(strategy)
                && !FirstLegConsts.STRATEGY_WEIGHT.equals(strategy)
                && !FirstLegConsts.STRATEGY_AMOUNT.equals(strategy)) {
            throw new BusinessException("分摊策略非法(QTY/WEIGHT/AMOUNT):" + strategy);
        }
        return strategy;
    }

    /** 插入主单并生成单号 FL+yyyyMMdd+4位seq;uk 撞号(并发)换序号重试,同 PaymentRecordService 先例 */
    private Long insertWithGeneratedNo(FirstLegShipment shipment) {
        String prefix = FirstLegConsts.SHIPMENT_NO_PREFIX
                + LocalDate.now(PullConsts.ZONE).format(DATE_PART);
        for (int attempt = 0; attempt < SEQ_RETRY_LIMIT; attempt++) {
            long seq = nextSeq(prefix) + attempt;
            shipment.setShipmentNo(prefix + String.format("%04d", seq));
            try {
                shipmentMapper.insert(shipment);
                return shipment.getId();
            } catch (DuplicateKeyException e) {
                log.warn("头程单号冲突,换序号重试 no={}", shipment.getShipmentNo());
            }
        }
        throw new BusinessException("头程单号生成失败(当日序号冲突),请重试");
    }

    /** 当日序号 = 同前缀已有单数 + 1(uk 冲突时由调用方换号重试) */
    private long nextSeq(String prefix) {
        Long count = shipmentMapper.selectCount(new LambdaQueryWrapper<FirstLegShipment>()
                .likeRight(FirstLegShipment::getShipmentNo, prefix));
        return (count == null ? 0L : count) + 1L;
    }
}
