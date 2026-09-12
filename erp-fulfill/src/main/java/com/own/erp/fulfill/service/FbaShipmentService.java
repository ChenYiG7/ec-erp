package com.own.erp.fulfill.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.GoodsQueryApi.SkuView;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.contract.WarehouseApi.WarehouseView;
import com.own.erp.fulfill.constant.FbaConsts;
import com.own.erp.fulfill.entity.FbaBox;
import com.own.erp.fulfill.entity.FbaBoxItem;
import com.own.erp.fulfill.entity.FbaShipment;
import com.own.erp.fulfill.entity.FbaShipmentDiff;
import com.own.erp.fulfill.entity.FbaShipmentItem;
import com.own.erp.fulfill.mapper.FbaBoxItemMapper;
import com.own.erp.fulfill.mapper.FbaBoxMapper;
import com.own.erp.fulfill.mapper.FbaQueryMapper;
import com.own.erp.fulfill.mapper.FbaShipmentDiffMapper;
import com.own.erp.fulfill.mapper.FbaShipmentItemMapper;
import com.own.erp.fulfill.mapper.FbaShipmentMapper;
import com.own.erp.fulfill.request.command.FbaReceiveRequest;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest.BoxItemSave;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest.BoxSave;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest.PlanItemSave;
import com.own.erp.fulfill.request.query.FbaShipmentQuery;
import com.own.erp.fulfill.response.FbaBoxItemResponse;
import com.own.erp.fulfill.response.FbaBoxResponse;
import com.own.erp.fulfill.response.FbaDiffResponse;
import com.own.erp.fulfill.response.FbaPlanItemResponse;
import com.own.erp.fulfill.response.FbaShipmentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.TreeMap;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 发货单服务(docs/plans/fba-shipment.md,V1 内部数据面):fba_shipment(+计划行/箱/箱内件/diff)
 *     域整域收口,Controller 不直连 Mapper(docs/07 §2.1),entity 不出本层。
 *     状态机 DRAFT→BOXED→SHIPPED→RECEIVING→CLOSED(CANCELED 旁路仅 DRAFT/BOXED),流转一律条件更新
 *     (WHERE 即守卫,docs/07 §6.3);单据域物理删除(仅 DRAFT/CANCELED,docs/07 §6.4)。
 *     SHIPPED 复合事务(预拍板 2026-09-10):装箱勾稽(Σbox_item 逐 SKU = 计划量,不平时拦截)+
 *     逐 SKU OUT_SHIP 经 InventoryChangeApi.change 唯一入口(biz_type=FBA_SHIPMENT,成本随货走移动加权成本账,
 *     unit_cost 传空由成本账取加权价);装箱不占库存(无 LOCK_SHIP,FBA 计划期长,长占恶化可用)。
 *     收货登记:SHIPPED→RECEIVING 首登 / RECEIVING 重复登记覆盖(先删后插幂等),diff 三态
 *     SHORT/EXTRA/OK 仿 RefundReconciliationService 纪律。跨域只走契约,禁横向依赖(铁律 2)
 */
@Slf4j
@Service
public class FbaShipmentService {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int SEQ_RETRY_LIMIT = 5;

    private final FbaShipmentMapper shipmentMapper;
    private final FbaShipmentItemMapper planItemMapper;
    private final FbaBoxMapper boxMapper;
    private final FbaBoxItemMapper boxItemMapper;
    private final FbaShipmentDiffMapper diffMapper;
    private final FbaQueryMapper queryMapper;
    private final WarehouseApi warehouseApi;
    private final GoodsQueryApi goodsQueryApi;
    private final InventoryChangeApi inventoryChangeApi;
    private final CurrentUserApi currentUserApi;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service,急切装配成环(docs/07 §2.2) */
    public FbaShipmentService(FbaShipmentMapper shipmentMapper,
                              FbaShipmentItemMapper planItemMapper,
                              FbaBoxMapper boxMapper,
                              FbaBoxItemMapper boxItemMapper,
                              FbaShipmentDiffMapper diffMapper,
                              FbaQueryMapper queryMapper,
                              @Lazy WarehouseApi warehouseApi,
                              @Lazy GoodsQueryApi goodsQueryApi,
                              @Lazy InventoryChangeApi inventoryChangeApi,
                              @Lazy CurrentUserApi currentUserApi) {
        this.shipmentMapper = shipmentMapper;
        this.planItemMapper = planItemMapper;
        this.boxMapper = boxMapper;
        this.boxItemMapper = boxItemMapper;
        this.diffMapper = diffMapper;
        this.queryMapper = queryMapper;
        this.warehouseApi = warehouseApi;
        this.goodsQueryApi = goodsQueryApi;
        this.inventoryChangeApi = inventoryChangeApi;
        this.currentUserApi = currentUserApi;
    }

    // ============================ 查询面 ============================

    /** 分页(店名/仓名 XML 联表投影,不带计划/装箱/对账明细) */
    public Page<FbaShipmentResponse> page(FbaShipmentQuery query) {
        return queryMapper.pageRows(new Page<>(query.getPageNo(), query.pageSize()), query);
    }

    /** 详情:主单 + 计划行 + 装箱树(箱+内件)+ 对账差异行;skuCode 经契约一次批量回填;不存在返回 null */
    public FbaShipmentResponse getById(Long id) {
        FbaShipment shipment = shipmentMapper.selectById(id);
        if (shipment == null) {
            return null;
        }
        List<FbaShipmentItem> planItems = listPlanItems(id);
        List<FbaBox> boxes = listBoxes(id);
        List<FbaBoxItem> items = listItemsOfBoxes(boxes);
        List<FbaShipmentDiff> diffs = diffMapper.selectList(new LambdaQueryWrapper<FbaShipmentDiff>()
                .eq(FbaShipmentDiff::getShipmentId, id)
                .orderByAsc(FbaShipmentDiff::getSkuId));

        Set<Long> skuIds = new LinkedHashSet<>();
        planItems.forEach(i -> skuIds.add(i.getSkuId()));
        items.forEach(i -> skuIds.add(i.getSkuId()));
        diffs.forEach(d -> skuIds.add(d.getSkuId()));
        Map<Long, String> skuCodeById = loadSkuCodeMap(skuIds);

        List<FbaPlanItemResponse> planResponses = planItems.stream()
                .map(i -> FbaPlanItemResponse.builder()
                        .id(i.getId()).shipmentId(i.getShipmentId()).skuId(i.getSkuId())
                        .skuCode(skuCodeById.get(i.getSkuId())).planQty(i.getPlanQty())
                        .build())
                .toList();
        Map<Long, List<FbaBoxItemResponse>> itemResponsesByBox = new HashMap<>();
        for (FbaBoxItem item : items) {
            itemResponsesByBox.computeIfAbsent(item.getBoxId(), k -> new ArrayList<>())
                    .add(FbaBoxItemResponse.builder()
                            .id(item.getId())
                            .boxId(item.getBoxId())
                            .skuId(item.getSkuId())
                            .skuCode(skuCodeById.get(item.getSkuId()))
                            .quantity(item.getQuantity())
                            .build());
        }
        List<FbaBoxResponse> boxResponses = boxes.stream()
                .map(b -> FbaBoxResponse.builder()
                        .id(b.getId()).shipmentId(b.getShipmentId()).boxNo(b.getBoxNo())
                        .weight(b.getWeight()).lengthCm(b.getLengthCm())
                        .widthCm(b.getWidthCm()).heightCm(b.getHeightCm()).createdAt(b.getCreatedAt())
                        .items(itemResponsesByBox.getOrDefault(b.getId(), List.of()))
                        .build())
                .toList();
        List<FbaDiffResponse> diffResponses = diffs.stream()
                .map(d -> FbaDiffResponse.builder()
                        .id(d.getId()).shipmentId(d.getShipmentId()).skuId(d.getSkuId())
                        .skuCode(skuCodeById.get(d.getSkuId()))
                        .shippedQty(d.getShippedQty()).receivedQty(d.getReceivedQty())
                        .diffType(d.getDiffType()).checkedAt(d.getCheckedAt())
                        .build())
                .toList();
        return FbaShipmentResponse.from(shipment)
                .withDetail(planResponses, boxResponses, diffResponses);
    }

    // ============================ 写侧:建单/改单/删除(仅 DRAFT) ============================

    /** 建单(DRAFT):发货仓 SELF 校验 + 计划行/装箱校验 → 生成 FB 单号落主单 → 落计划行/箱/内件(同事务) */
    @Transactional(rollbackFor = Exception.class)
    public Long save(FbaShipmentSaveRequest request) {
        validateWarehouse(request.warehouseId());
        validatePlanItems(request.planItems());
        validateBoxes(request.boxes(), request.planItems());
        FbaShipment shipment = FbaShipment.builder()
                .shopId(request.shopId())
                .marketplace(StrUtil.trim(request.marketplace()))
                .warehouseId(request.warehouseId())
                .platformShipmentId(StrUtil.trimToNull(request.platformShipmentId()))
                .status(FbaConsts.STATUS_DRAFT)
                .remark(StrUtil.trimToNull(request.remark()))
                .createdBy(currentUserApi.currentUserId())
                .build();
        Long id = insertWithGeneratedNo(shipment);
        replacePlanItems(id, request.planItems());
        replaceBoxes(id, request.boxes());
        log.info("FBA发货单建单成功 shipmentId={} no={} 计划行={} 箱数={}", id, shipment.getShipmentNo(),
                request.planItems().size(), request.boxes() == null ? 0 : request.boxes().size());
        return id;
    }

    /**
     * 改单:仅 DRAFT(行锁读串行化与装箱/发货竞态);表头更新 + 计划行/装箱整体替换(先删后插)。
     * BOXED 起计划与箱内容冻结,改单走取消重建
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FbaShipmentSaveRequest request) {
        FbaShipment exist = shipmentMapper.selectByIdForUpdate(id);
        if (exist == null) {
            throw new BusinessException("FBA发货单不存在:" + id);
        }
        if (!FbaConsts.STATUS_DRAFT.equals(exist.getStatus())) {
            throw new BusinessException("仅草稿状态可修改,当前状态:" + exist.getStatus());
        }
        validateWarehouse(request.warehouseId());
        validatePlanItems(request.planItems());
        validateBoxes(request.boxes(), request.planItems());
        FbaShipment shipment = FbaShipment.builder()
                .id(id)
                .shopId(request.shopId())
                .marketplace(StrUtil.trim(request.marketplace()))
                .warehouseId(request.warehouseId())
                .platformShipmentId(StrUtil.trimToNull(request.platformShipmentId()))
                .remark(StrUtil.trimToNull(request.remark()))
                .build();
        shipmentMapper.updateById(shipment);
        deletePlanItems(id);
        deleteBoxes(id);
        replacePlanItems(id, request.planItems());
        replaceBoxes(id, request.boxes());
        log.info("FBA发货单改单成功 shipmentId={}", id);
    }

    /** 删除:仅 DRAFT/CANCELED(单据域物理删除,docs/07 §6.4);SHIPPED 起库存已动账禁删 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FbaShipment exist = shipmentMapper.selectById(id);
        if (exist == null) {
            return;
        }
        String status = exist.getStatus();
        if (!FbaConsts.STATUS_DRAFT.equals(status) && !FbaConsts.STATUS_CANCELED.equals(status)) {
            throw new BusinessException("已装箱/已发出/收货中的 FBA 单禁止删除(库存动账事实需可追溯),当前状态:" + status);
        }
        deletePlanItems(id);
        deleteBoxes(id);
        shipmentMapper.deleteById(id);
    }

    // ============================ 写侧:状态机动作 ============================

    /**
     * 装箱完成:DRAFT→BOXED 条件更新守卫;至少 1 箱有件且逐 SKU Σ箱内件 = 计划量(预检给早期反馈,
     * SHIPPED 时权威复检——cas 与校验同事务,校验失败随事务回滚)
     */
    @Transactional(rollbackFor = Exception.class)
    public void box(Long id) {
        if (shipmentMapper.casBox(id) == 0) {
            throw new BusinessException("装箱失败:FBA单不存在或不是草稿状态:" + id);
        }
        List<FbaBox> boxes = listBoxes(id);
        List<FbaBoxItem> items = listItemsOfBoxes(boxes);
        if (CollUtil.isEmpty(boxes) || items.isEmpty()) {
            throw new BusinessException("装箱失败:至少需要 1 个箱且箱内有 SKU,请先录入装箱明细:" + id);
        }
        Map<Long, Integer> planBySku = planQtyMap(id);
        Map<Long, Integer> boxBySku = aggregateQuantities(items);
        checkReconcile(planBySku, boxBySku);
        log.info("FBA发货单装箱完成 shipmentId={} 箱数={} 件数={}", id, boxes.size(), items.size());
    }

    /**
     * 确认发出(BOXED→SHIPPED,核心复合事务):cas 守卫占位 → 装箱勾稽(Σbox_item 逐 SKU = 计划量,
     * 不平拦截防半装发出)→ 逐 SKU OUT_SHIP 经 InventoryChangeApi.change 唯一入口(铁律 4;数量取计划量,
     * 与箱内件勾稽后等值;可用不足 change 抛错整单回滚)→ 回填 shipped_at。
     * 成本随货走移动加权成本账:OUT_SHIP 的 unit_cost 由 InventoryCostService 自行取加权价,传入值忽略
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id) {
        if (shipmentMapper.casShip(id) == 0) {
            throw new BusinessException("发货失败:FBA单不存在或不是已装箱状态:" + id);
        }
        FbaShipment shipment = shipmentMapper.selectById(id);
        List<FbaBoxItem> items = listItemsOfBoxes(listBoxes(id));
        if (items.isEmpty()) {
            throw new BusinessException("发货失败:装箱无明细:" + id);
        }
        Map<Long, Integer> planBySku = planQtyMap(id);
        Map<Long, Integer> boxBySku = aggregateQuantities(items);
        checkReconcile(planBySku, boxBySku);

        for (Long skuId : planBySku.keySet().stream().sorted().toList()) {
            inventoryChangeApi.change(InventoryChangeCommand.builder()
                    .skuId(skuId)
                    .warehouseId(shipment.getWarehouseId())
                    .quantity(-planBySku.get(skuId))
                    .flowType(InventoryConsts.FLOW_TYPE_OUT_SHIP)
                    .bizType(FbaConsts.BIZ_TYPE_FBA_SHIPMENT)
                    .bizId(id)
                    // OUT_SHIP 成本由移动加权成本账自行结转,unit_cost 不传(docs/03 §4 唯一入口口径)
                    .unitCost(null)
                    .remark("FBA发货单:" + shipment.getShipmentNo())
                    .createdBy(shipment.getCreatedBy())
                    .build());
        }
        shipment.setShippedAt(LocalDateTime.now(PullConsts.ZONE));
        shipment.setStatus(FbaConsts.STATUS_SHIPPED);
        shipmentMapper.updateById(shipment);
        log.info("FBA发货单确认发出 shipmentId={} no={} 出库仓={} SKU种数={} 总件数={}",
                id, shipment.getShipmentNo(), shipment.getWarehouseId(),
                planBySku.size(), planBySku.values().stream().mapToInt(Integer::intValue).sum());
    }

    /**
     * 收货登记(SHIPPED→RECEIVING 首登 / RECEIVING 重复登记覆盖,复合事务):首次经 casReceive 占位,
     * 已在 RECEIVING 态放行(先删后插幂等重登)→ 校验(发出 SKU 必须全部在列,未登记按 0=SHORT,
     * 缺行即拦防半量登记失真;收货量非负;SKU 存在性)→ 生成 diff 三态(SHORT/EXTRA/OK,uk 幂等底座)→ 回填 received_at。
     * CLOSED 后冻结禁再登记(状态机守卫)
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerReceive(Long id, FbaReceiveRequest request) {
        if (shipmentMapper.casReceive(id) == 0) {
            FbaShipment exist = shipmentMapper.selectById(id);
            if (exist == null) {
                throw new BusinessException("FBA发货单不存在:" + id);
            }
            if (!FbaConsts.STATUS_RECEIVING.equals(exist.getStatus())) {
                throw new BusinessException("收货登记失败:仅已发出/收货登记中状态可登记,当前状态:" + exist.getStatus());
            }
        }
        List<FbaBoxItem> items = listItemsOfBoxes(listBoxes(id));
        Map<Long, Integer> shippedBySku = aggregateQuantities(items);
        if (shippedBySku.isEmpty()) {
            throw new BusinessException("收货登记失败:发出明细缺失(装箱数据异常),请核查:" + id);
        }
        Map<Long, Integer> receivedBySku = new TreeMap<>();
        Set<Long> dupCheck = new HashSet<>();
        Set<Long> allSkuIds = new LinkedHashSet<>();
        for (FbaReceiveRequest.ReceiveItem item : request.items()) {
            if (item.skuId() == null || item.receivedQty() == null || item.receivedQty() < 0) {
                throw new BusinessException("收货行非法:SKU 必填且收货数量不能为负");
            }
            if (!dupCheck.add(item.skuId())) {
                throw new BusinessException("收货行 SKU 重复,请合并为一行:SKU=" + item.skuId());
            }
            receivedBySku.put(item.skuId(), item.receivedQty());
            allSkuIds.add(item.skuId());
        }
        // 发出 SKU 必须全部在列(缺=未登记,防半量登记把 SHORT 误吞成"没录");整单完整性由后端守卫
        List<Long> missing = shippedBySku.keySet().stream()
                .filter(skuId -> !receivedBySku.containsKey(skuId)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("收货登记失败:发出 SKU 未全部登记收货量(未登记按 0 计需显式填 0),缺 SKU=" + missing);
        }
        allSkuIds.addAll(shippedBySku.keySet());
        Map<Long, SkuView> skuById = loadSkuMap(allSkuIds);
        List<Long> unknown = allSkuIds.stream().filter(skuId -> !skuById.containsKey(skuId)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException("收货行 SKU 不存在:" + unknown);
        }

        LocalDateTime now = LocalDateTime.now(PullConsts.ZONE);
        // 重复登记覆盖 = 先删后插(uk(shipment_id,sku_id) 兜底;CLOSED 前可反复修正,关闭即冻结)
        diffMapper.delete(new LambdaQueryWrapper<FbaShipmentDiff>()
                .eq(FbaShipmentDiff::getShipmentId, id));
        for (Long skuId : new TreeMap<>(receivedBySku).keySet()) {
            int shipped = shippedBySku.getOrDefault(skuId, 0);
            int received = receivedBySku.get(skuId);
            if (shipped == 0 && received == 0) {
                // 计划外 SKU 登记收货 0:无对账信息量,不落 0/0 噪音行
                continue;
            }
            String diffType = received < shipped ? FbaConsts.DIFF_SHORT
                    : received > shipped ? FbaConsts.DIFF_EXTRA : FbaConsts.DIFF_OK;
            diffMapper.insert(FbaShipmentDiff.builder()
                    .shipmentId(id)
                    .skuId(skuId)
                    .shippedQty(shipped)
                    .receivedQty(received)
                    .diffType(diffType)
                    .checkedAt(now)
                    .build());
        }
        FbaShipment mark = FbaShipment.builder().id(id).receivedAt(now).build();
        shipmentMapper.updateById(mark);
        long shortCount = receivedBySku.entrySet().stream()
                .filter(e -> e.getValue() < shippedBySku.getOrDefault(e.getKey(), 0)).count();
        long extraCount = receivedBySku.entrySet().stream()
                .filter(e -> e.getValue() > shippedBySku.getOrDefault(e.getKey(), 0)).count();
        log.info("FBA发货单收货登记 shipmentId={} SKU种数={} SHORT={} EXTRA={}", id, receivedBySku.size(),
                shortCount, extraCount);
    }

    /** 关闭:RECEIVING→CLOSED 条件更新守卫(diff 对账事实冻结) */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        if (shipmentMapper.casClose(id) == 0) {
            throw new BusinessException("关闭失败:FBA单不存在或不是收货登记中状态:" + id);
        }
        log.info("FBA发货单关闭 shipmentId={}", id);
    }

    /** 取消:DRAFT/BOXED→CANCELED(SHIPPED 起库存已动账禁取消,逆向=作废重开随实际使用拍板) */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        if (shipmentMapper.casCancel(id) == 0) {
            throw new BusinessException("取消失败:仅草稿/已装箱状态可取消(已发出起库存已动账):" + id);
        }
        log.info("FBA发货单取消 shipmentId={}", id);
    }

    // ============================ Job 取数口(FbaReconciliationJob) ============================

    /**
     * 超期未登记收货/未关闭的发货单(SHIPPED/RECEIVING 且 shipped_at <= deadline),
     * 供 FbaReconciliationJob 每日扫描提醒;eq 逐条规避 LambdaWrapper.in() 单测急切解析坑(docs/07 §10)
     */
    public List<FbaShipment> listOverdueForReceive(LocalDateTime deadline) {
        List<FbaShipment> result = new ArrayList<>();
        result.addAll(shipmentMapper.selectList(new LambdaQueryWrapper<FbaShipment>()
                .eq(FbaShipment::getStatus, FbaConsts.STATUS_SHIPPED)
                .le(FbaShipment::getShippedAt, deadline)
                .orderByAsc(FbaShipment::getShippedAt)));
        result.addAll(shipmentMapper.selectList(new LambdaQueryWrapper<FbaShipment>()
                .eq(FbaShipment::getStatus, FbaConsts.STATUS_RECEIVING)
                .le(FbaShipment::getShippedAt, deadline)
                .orderByAsc(FbaShipment::getShippedAt)));
        return result;
    }

    // ============================ 装箱勾稽 ============================

    /** 勾稽:箱内件聚合与计划行逐 SKU 相等(缺 SKU/多 SKU/数量不平均拦截,列明差异) */
    private void checkReconcile(Map<Long, Integer> planBySku, Map<Long, Integer> boxBySku) {
        List<Long> missing = planBySku.keySet().stream()
                .filter(skuId -> !boxBySku.containsKey(skuId)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("装箱勾稽失败:计划 SKU 未装箱,SKU=" + missing);
        }
        List<Long> extra = boxBySku.keySet().stream()
                .filter(skuId -> !planBySku.containsKey(skuId)).sorted().toList();
        if (!extra.isEmpty()) {
            throw new BusinessException("装箱勾稽失败:箱内含计划外 SKU,SKU=" + extra);
        }
        List<String> mismatch = planBySku.keySet().stream()
                .filter(skuId -> !planBySku.get(skuId).equals(boxBySku.get(skuId)))
                .sorted().map(skuId -> "SKU=" + skuId + " 计划" + planBySku.get(skuId) + "/装箱" + boxBySku.get(skuId))
                .toList();
        if (!mismatch.isEmpty()) {
            throw new BusinessException("装箱勾稽失败:数量与计划不平," + mismatch);
        }
    }

    /** 计划量映射(shipment_id → skuId → plan_qty) */
    private Map<Long, Integer> planQtyMap(Long shipmentId) {
        Map<Long, Integer> planBySku = new TreeMap<>();
        for (FbaShipmentItem item : listPlanItems(shipmentId)) {
            planBySku.put(item.getSkuId(), item.getPlanQty());
        }
        return planBySku;
    }

    /** 跨箱汇总每 SKU 总件数(同 SKU 可分布在多箱;箱内 uk 已保证不重复) */
    private Map<Long, Integer> aggregateQuantities(List<FbaBoxItem> items) {
        Map<Long, Integer> qtyBySku = new TreeMap<>();
        for (FbaBoxItem item : items) {
            qtyBySku.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        return qtyBySku;
    }

    // ============================ 内部装配/校验 ============================

    /** 仓型校验:发货仓必须 SELF 国内仓(出库动账在此仓;FBA 目的仓档案存在但不入账,V1 不校验目的仓) */
    private void validateWarehouse(Long warehouseId) {
        WarehouseView wh = warehouseApi.findWarehouseViewById(warehouseId);
        if (wh == null) {
            throw new BusinessException("国内发货仓不存在:" + warehouseId);
        }
        if (!FbaConsts.WH_TYPE_SELF.equals(wh.whType())) {
            throw new BusinessException("FBA发货仓必须是国内自仓(SELF),当前为 " + wh.whType()
                    + ":" + wh.whName());
        }
    }

    /** 计划行校验:非空、同单 SKU 不重复、件数>0、SKU 经契约存在性校验 */
    private void validatePlanItems(List<PlanItemSave> planItems) {
        if (CollUtil.isEmpty(planItems)) {
            throw new BusinessException("计划行不能为空,至少录入 1 行 SKU 清单");
        }
        Set<Long> skuIds = new HashSet<>();
        for (PlanItemSave item : planItems) {
            if (item.skuId() == null || item.planQty() == null || item.planQty() <= 0) {
                throw new BusinessException("计划行非法:SKU 必填且计划数量大于0");
            }
            if (!skuIds.add(item.skuId())) {
                throw new BusinessException("计划行 SKU 重复,请合并为一行:SKU=" + item.skuId());
            }
        }
        Map<Long, SkuView> skuById = loadSkuMap(skuIds);
        List<Long> missing = skuIds.stream().filter(id -> !skuById.containsKey(id)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new BusinessException("计划行 SKU 不存在:" + missing);
        }
    }

    /** 装箱校验:箱号单内唯一;箱内件数>0、同箱 SKU 不重复;箱内 SKU 必须都在计划行内(草稿允许空箱单) */
    private void validateBoxes(List<BoxSave> boxes, List<PlanItemSave> planItems) {
        if (CollUtil.isEmpty(boxes)) {
            return;
        }
        Set<Long> planSkuIds = new HashSet<>();
        for (PlanItemSave item : planItems) {
            planSkuIds.add(item.skuId());
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
                if (!planSkuIds.contains(item.skuId())) {
                    throw new BusinessException("箱内含计划外 SKU(箱号 " + boxNo + "),请先加入计划行:SKU="
                            + item.skuId());
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

    /** 落计划行(builder 纯构造装配,校验已过) */
    private void replacePlanItems(Long shipmentId, List<PlanItemSave> planItems) {
        for (PlanItemSave itemReq : planItems) {
            planItemMapper.insert(FbaShipmentItem.builder()
                    .shipmentId(shipmentId)
                    .skuId(itemReq.skuId())
                    .planQty(itemReq.planQty())
                    .build());
        }
    }

    /** 落箱与箱内件(builder 纯构造装配,校验已过) */
    private void replaceBoxes(Long shipmentId, List<BoxSave> boxes) {
        if (CollUtil.isEmpty(boxes)) {
            return;
        }
        for (BoxSave boxReq : boxes) {
            FbaBox box = FbaBox.builder()
                    .shipmentId(shipmentId)
                    .boxNo(StrUtil.trim(boxReq.boxNo()))
                    .weight(boxReq.weight())
                    .lengthCm(boxReq.lengthCm())
                    .widthCm(boxReq.widthCm())
                    .heightCm(boxReq.heightCm())
                    .build();
            boxMapper.insert(box);
            for (BoxItemSave itemReq : boxReq.items()) {
                boxItemMapper.insert(FbaBoxItem.builder()
                        .boxId(box.getId())
                        .skuId(itemReq.skuId())
                        .quantity(itemReq.quantity())
                        .build());
            }
        }
    }

    /** 删除单的全部计划行(供改单先删后插/物理删除) */
    private void deletePlanItems(Long shipmentId) {
        planItemMapper.delete(new LambdaQueryWrapper<FbaShipmentItem>()
                .eq(FbaShipmentItem::getShipmentId, shipmentId));
    }

    /** 删除单的全部箱内件 + 箱(逐箱 eq 删除件,规避 LambdaWrapper.in() 纯单测急切解析坑,docs/07 §10) */
    private void deleteBoxes(Long shipmentId) {
        List<FbaBox> boxes = listBoxes(shipmentId);
        for (FbaBox box : boxes) {
            boxItemMapper.delete(new LambdaQueryWrapper<FbaBoxItem>()
                    .eq(FbaBoxItem::getBoxId, box.getId()));
        }
        boxMapper.delete(new LambdaQueryWrapper<FbaBox>()
                .eq(FbaBox::getShipmentId, shipmentId));
    }

    private List<FbaShipmentItem> listPlanItems(Long shipmentId) {
        return planItemMapper.selectList(new LambdaQueryWrapper<FbaShipmentItem>()
                .eq(FbaShipmentItem::getShipmentId, shipmentId)
                .orderByAsc(FbaShipmentItem::getId));
    }

    private List<FbaBox> listBoxes(Long shipmentId) {
        return boxMapper.selectList(new LambdaQueryWrapper<FbaBox>()
                .eq(FbaBox::getShipmentId, shipmentId)
                .orderByAsc(FbaBox::getId));
    }

    private List<FbaBoxItem> listItemsOfBoxes(List<FbaBox> boxes) {
        List<FbaBoxItem> items = new ArrayList<>();
        for (FbaBox box : boxes) {
            items.addAll(boxItemMapper.selectList(new LambdaQueryWrapper<FbaBoxItem>()
                    .eq(FbaBoxItem::getBoxId, box.getId())
                    .orderByAsc(FbaBoxItem::getId)));
        }
        return items;
    }

    /** 契约批量取 SKU 视图(存在性校验;eq/批量入参,单测环境不触 Wrapper.in) */
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

    /** 插入主单并生成单号 FB+yyyyMMdd+4位seq;uk 撞号(并发)换序号重试,同 FirstLegShipmentService 先例 */
    private Long insertWithGeneratedNo(FbaShipment shipment) {
        String prefix = FbaConsts.SHIPMENT_NO_PREFIX
                + LocalDate.now(PullConsts.ZONE).format(DATE_PART);
        for (int attempt = 0; attempt < SEQ_RETRY_LIMIT; attempt++) {
            long seq = nextSeq(prefix) + attempt;
            shipment.setShipmentNo(prefix + String.format("%04d", seq));
            try {
                shipmentMapper.insert(shipment);
                return shipment.getId();
            } catch (DuplicateKeyException e) {
                log.warn("FBA单号冲突,换序号重试 no={}", shipment.getShipmentNo());
            }
        }
        throw new BusinessException("FBA单号生成失败(当日序号冲突),请重试");
    }

    /** 当日序号 = 同前缀已有单数 + 1(uk 冲突时由调用方换号重试) */
    private long nextSeq(String prefix) {
        Long count = shipmentMapper.selectCount(new LambdaQueryWrapper<FbaShipment>()
                .likeRight(FbaShipment::getShipmentNo, prefix));
        return (count == null ? 0L : count) + 1L;
    }
}
