package com.own.erp.inventory.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.constant.StocktakeConsts;
import com.own.erp.inventory.entity.Inventory;
import com.own.erp.inventory.entity.InventoryFlow;
import com.own.erp.inventory.entity.StocktakeItem;
import com.own.erp.inventory.entity.StocktakeOrder;
import com.own.erp.inventory.mapper.StocktakeItemMapper;
import com.own.erp.inventory.mapper.StocktakeOrderMapper;
import com.own.erp.inventory.request.command.StocktakeCountRequest;
import com.own.erp.inventory.request.command.StocktakeOrderSaveRequest;
import com.own.erp.inventory.request.query.StocktakeOrderQuery;
import com.own.erp.inventory.response.StocktakeItemResponse;
import com.own.erp.inventory.response.StocktakeOrderResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点单服务:stocktake_order(+stocktake_item 子表)域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     状态机(仓内作业,docs/plans/warehouse-ops.md §2.1):DRAFT(建单即快照)→ start → COUNTING(录实盘)
 *     → submit(实盘录齐)→ PENDING_ADJUST → generateAdjust(差异动账)→ ADJUSTED → close → CLOSED;
 *     CANCELED 旁路(未动账三态可取消)。流转一律条件更新(WHERE 即守卫,docs/07 §6.3 禁先查后改)。
 *     双口径(2026-09-11 预拍板):建单即快照账面在库进 stocktake_item.book_qty(snapshot_at 记时点,展示用);
 *     generateAdjust 按**确认时点**账面 re-diff(InventoryService.currentOnHand 取数),差异行逐行经
 *     InventoryService.change()(flow_type=ADJUST、biz_type=STOCKTAKE、biz_id=盘点单 id)动账并回填 adjust_flow_id。
 *     整单原子:任一行 change() 被守卫拒(可用/在途不足)或成本账结存转负即整单回滚;成本账随 change() 同事务
 *     按当时移动加权价进出(盘盈入账/盘亏结转同 OUT_SHIP 口径,拍板点③),禁旁路动 sku_cost_state。
 *     跨域存在性校验走 erp-contract(禁横向依赖,铁律 2);账本理论约束:盘亏幅度不得超过成本账全局结存,
 *     超出(账实漂移)由 InventoryCostService 抛异常拒绝,属设计内拦截非缺陷
 */
@Service
public class StocktakeOrderService {

    private final StocktakeOrderMapper stocktakeOrderMapper;
    private final StocktakeItemMapper stocktakeItemMapper;
    private final InventoryService inventoryService;
    private final WarehouseApi warehouseApi;
    private final GoodsSkuApi goodsSkuApi;
    private final CurrentUserApi currentUserApi;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service,急切装配成环(docs/07 §2.2) */
    public StocktakeOrderService(StocktakeOrderMapper stocktakeOrderMapper,
                                 StocktakeItemMapper stocktakeItemMapper,
                                 InventoryService inventoryService,
                                 @Lazy WarehouseApi warehouseApi,
                                 @Lazy GoodsSkuApi goodsSkuApi,
                                 @Lazy CurrentUserApi currentUserApi) {
        this.stocktakeOrderMapper = stocktakeOrderMapper;
        this.stocktakeItemMapper = stocktakeItemMapper;
        this.inventoryService = inventoryService;
        this.warehouseApi = warehouseApi;
        this.goodsSkuApi = goodsSkuApi;
        this.currentUserApi = currentUserApi;
    }

    /** 分页查询(按 id 倒序;过滤:盘点单号模糊/仓库/状态);列表不带明细 */
    public Page<StocktakeOrderResponse> page(StocktakeOrderQuery query) {
        Page<StocktakeOrder> result = stocktakeOrderMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<StocktakeOrder>()
                        .like(StrUtil.isNotBlank(query.getStocktakeNo()), StocktakeOrder::getStocktakeNo, query.getStocktakeNo())
                        .eq(query.getWarehouseId() != null, StocktakeOrder::getWarehouseId, query.getWarehouseId())
                        .eq(StrUtil.isNotBlank(query.getStatus()), StocktakeOrder::getStatus, query.getStatus())
                        .orderByDesc(StocktakeOrder::getId));
        Page<StocktakeOrderResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(StocktakeOrderResponse::from).toList());
        return responsePage;
    }

    /** 详情带明细(withItems wither 副本);不存在返回 null */
    public StocktakeOrderResponse getById(Long id) {
        StocktakeOrder stocktakeOrder = stocktakeOrderMapper.selectById(id);
        if (stocktakeOrder == null) {
            return null;
        }
        List<StocktakeItemResponse> items = listItems(id).stream().map(StocktakeItemResponse::from).toList();
        return StocktakeOrderResponse.from(stocktakeOrder).withItems(items);
    }

    /**
     * 建单(DRAFT):校验仓库/范围 → 落主表(状态固定 DRAFT) → **快照账面在库**落明细行
     * (scope ALL = 该仓 inventory 全部现有行;SKU_SET = 入参 SKU 集,无行 SKU 账面按 0)
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(StocktakeOrderSaveRequest request) {
        validateRefs(request);
        StocktakeOrder stocktakeOrder = request.toEntity();
        // 写前回填服务端管理列(setter 白名单):状态固定草稿,createdBy 按 SecurityContext
        stocktakeOrder.setStatus(StocktakeConsts.STATUS_DRAFT);
        stocktakeOrder.setCreatedBy(currentUserApi.currentUserId());
        try {
            stocktakeOrderMapper.insert(stocktakeOrder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("盘点单号已存在:" + request.stocktakeNo());
        }
        snapshotItems(stocktakeOrder.getId(), request);
        return stocktakeOrder.getId();
    }

    /**
     * 更新:仅 DRAFT 可改(已开始盘点/已动账禁改,改单走取消重建);明细整体重做快照。
     * 行锁读(selectByIdForUpdate)串行化与 start/generateAdjust 的竞态(docs/07 §6.3)
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StocktakeOrderSaveRequest request) {
        StocktakeOrder exist = stocktakeOrderMapper.selectByIdForUpdate(id);
        if (exist == null) {
            throw new BusinessException("盘点单不存在:" + id);
        }
        if (!StocktakeConsts.STATUS_DRAFT.equals(exist.getStatus())) {
            throw new BusinessException("仅草稿状态可修改,当前:" + exist.getStatus());
        }
        validateRefs(request);
        StocktakeOrder stocktakeOrder = request.toEntity();
        stocktakeOrder.setId(id);
        try {
            stocktakeOrderMapper.updateById(stocktakeOrder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("盘点单号已存在:" + request.stocktakeNo());
        }
        stocktakeItemMapper.delete(new LambdaQueryWrapper<StocktakeItem>()
                .eq(StocktakeItem::getStocktakeId, id));
        snapshotItems(id, request);
    }

    /** 开始盘点:DRAFT → COUNTING(条件更新占位,并发重复开始仅一个成功) */
    public void start(Long id) {
        if (stocktakeOrderMapper.casStatus(id, StocktakeConsts.STATUS_DRAFT, StocktakeConsts.STATUS_COUNTING) == 0) {
            throw new BusinessException("开始盘点失败:盘点单不存在或不是草稿状态");
        }
    }

    /**
     * 录入实盘(COUNTING,允许多次补录/修正):逐行覆盖 counted_qty 并即时算 diff_qty=实盘-建单快照账面
     * (展示口径;最终差异以 generateAdjust 的确认时点 re-diff 为准)。行必须属于本盘点单
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordCounts(Long id, StocktakeCountRequest request) {
        StocktakeOrder exist = stocktakeOrderMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("盘点单不存在:" + id);
        }
        if (!StocktakeConsts.STATUS_COUNTING.equals(exist.getStatus())) {
            throw new BusinessException("仅盘点中状态可录入实盘,当前:" + exist.getStatus());
        }
        if (request == null || CollUtil.isEmpty(request.lines())) {
            throw new BusinessException("实盘明细不能为空");
        }
        Map<Long, StocktakeItem> itemBySku = new HashMap<>();
        for (StocktakeItem item : listItems(id)) {
            itemBySku.put(item.getSkuId(), item);
        }
        Set<Long> seen = new HashSet<>();
        for (StocktakeCountRequest.StocktakeCountLine line : request.lines()) {
            if (line.skuId() == null || line.countedQty() == null || line.countedQty() < 0) {
                throw new BusinessException("实盘行非法:SKU 必填且数量 ≥ 0");
            }
            if (!seen.add(line.skuId())) {
                throw new BusinessException("同一 SKU 在实盘录入内重复:skuId=" + line.skuId());
            }
            StocktakeItem item = itemBySku.get(line.skuId());
            if (item == null) {
                throw new BusinessException("SKU 不属于本盘点单:skuId=" + line.skuId());
            }
            stocktakeItemMapper.updateById(StocktakeItem.builder()
                    .id(item.getId())
                    .countedQty(line.countedQty())
                    .diffQty(line.countedQty() - nvl(item.getBookQty()))
                    .build());
        }
    }

    /** 实盘录齐提交:COUNTING → PENDING_ADJUST(占位后校验所有明细行 counted_qty 非空,缺行随事务回滚) */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        if (stocktakeOrderMapper.casStatus(id, StocktakeConsts.STATUS_COUNTING,
                StocktakeConsts.STATUS_PENDING_ADJUST) == 0) {
            throw new BusinessException("提交盘点失败:盘点单不存在或不是盘点中状态");
        }
        boolean allCounted = listItems(id).stream().allMatch(item -> item.getCountedQty() != null);
        if (!allCounted) {
            throw new BusinessException("存在未录入实盘的明细行,禁止提交");
        }
    }

    /**
     * 生成调整(复合事务核心,PENDING_ADJUST → ADJUSTED):占位条件更新(并发双确认仅一个成功,失败随事务回滚)
     * → 逐行按**确认时点**账面 re-diff(diff = 实盘 - currentOnHand)→ 差异非 0 行走 InventoryService.change()
     * (flow_type=ADJUST,biz_type=STOCKTAKE,biz_id=盘点单 id;Δ>0 盘盈/Δ<0 盘亏,守卫可用+Δ≥0)
     * → 回填 diff_qty/adjust_flow_id 与 confirmed_by/confirmed_at。任一行失败整单回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateAdjust(Long id) {
        if (stocktakeOrderMapper.casStatus(id, StocktakeConsts.STATUS_PENDING_ADJUST,
                StocktakeConsts.STATUS_ADJUSTED) == 0) {
            throw new BusinessException("生成调整失败:盘点单不存在或不是待调整状态");
        }
        StocktakeOrder order = stocktakeOrderMapper.selectById(id);
        List<StocktakeItem> items = listItems(id);
        if (items.stream().anyMatch(item -> item.getCountedQty() == null)) {
            throw new BusinessException("存在未录入实盘的明细行,禁止生成调整");
        }
        for (StocktakeItem item : items) {
            int diff = item.getCountedQty() - inventoryService.currentOnHand(item.getSkuId(), order.getWarehouseId());
            Long flowId = null;
            if (diff != 0) {
                try {
                    flowId = inventoryService.change(InventoryFlow.builder()
                            .skuId(item.getSkuId())
                            .warehouseId(order.getWarehouseId())
                            .quantity(diff)
                            .flowType(InventoryConsts.FLOW_TYPE_ADJUST)
                            .bizType(InventoryConsts.BIZ_TYPE_STOCKTAKE)
                            .bizId(id)
                            .remark("盘点调整:" + order.getStocktakeNo())
                            .createdBy(order.getCreatedBy())
                            .build());
                } catch (BusinessException e) {
                    throw new BusinessException("盘点差异调整失败(SKU=" + item.getSkuId()
                            + ",差异=" + diff + "):" + e.getMessage());
                }
            }
            stocktakeItemMapper.updateById(StocktakeItem.builder()
                    .id(item.getId())
                    .diffQty(diff)
                    .adjustFlowId(flowId)
                    .build());
        }
        stocktakeOrderMapper.updateById(StocktakeOrder.builder()
                .id(id)
                .confirmedBy(currentUserApi.currentUserId())
                .confirmedAt(LocalDateTime.now())
                .build());
    }

    /** 关闭:ADJUSTED → CLOSED(终态;只有已调整可达,保证差异必处理或明确放弃) */
    public void close(Long id) {
        if (stocktakeOrderMapper.casStatus(id, StocktakeConsts.STATUS_ADJUSTED, StocktakeConsts.STATUS_CLOSED) == 0) {
            throw new BusinessException("关闭失败:盘点单不存在或未完成调整");
        }
    }

    /**
     * 取消:未动账三态(DRAFT/COUNTING/PENDING_ADJUST)→ CANCELED 条件更新占位
     * (并发取消/取消与开始·提交竞态仅一个成功);已调整/已关闭不可取消(差异已动账,冲销走反向盘点)
     */
    public void cancel(Long id) {
        StocktakeOrder exist = stocktakeOrderMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("盘点单不存在:" + id);
        }
        String status = exist.getStatus();
        if (!StocktakeConsts.STATUS_DRAFT.equals(status) && !StocktakeConsts.STATUS_COUNTING.equals(status)
                && !StocktakeConsts.STATUS_PENDING_ADJUST.equals(status)) {
            throw new BusinessException("取消失败:当前状态不可取消(已调整/已关闭),状态:" + status);
        }
        if (stocktakeOrderMapper.casStatus(id, status, StocktakeConsts.STATUS_CANCELED) == 0) {
            throw new BusinessException("取消失败:盘点单状态已变化,请刷新重试:" + id);
        }
    }

    /** 删除:已调整/已关闭禁删(差异已动账,删单致账实无法追溯);其余状态连明细同事务硬删 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StocktakeOrder exist = stocktakeOrderMapper.selectById(id);
        if (exist == null) {
            return;
        }
        if (StocktakeConsts.STATUS_ADJUSTED.equals(exist.getStatus())
                || StocktakeConsts.STATUS_CLOSED.equals(exist.getStatus())) {
            throw new BusinessException("已调整/已关闭盘点单禁止删除(差异已动账):" + id);
        }
        stocktakeItemMapper.delete(new LambdaQueryWrapper<StocktakeItem>()
                .eq(StocktakeItem::getStocktakeId, id));
        stocktakeOrderMapper.deleteById(id);
    }

    /** 仓库盘点单引用数(#30 余量,仓库删除校验经 erp-contract WarehouseApi 暴露):>0 即有盘点业务,禁删仓库 */
    public long countByWarehouseId(Long warehouseId) {
        if (warehouseId == null) {
            return 0;
        }
        Long count = stocktakeOrderMapper.selectCount(new LambdaQueryWrapper<StocktakeOrder>()
                .eq(StocktakeOrder::getWarehouseId, warehouseId));
        return count == null ? 0L : count;
    }

    /** 明细实体列表(同模块内取数,entity 不出本模块;按 id 升序稳定排序) */
    private List<StocktakeItem> listItems(Long stocktakeId) {
        return stocktakeItemMapper.selectList(new LambdaQueryWrapper<StocktakeItem>()
                .eq(StocktakeItem::getStocktakeId, stocktakeId)
                .orderByAsc(StocktakeItem::getId));
    }

    /**
     * 账面快照落明细(建单/改单共用):ALL 取该仓全部库存行,SKU_SET 按入参 SKU 逐行取(缺行按账面 0);
     * snapshotAt 统一取同一时点(建单时刻),防"边快照边动账"导致行间时点不一致
     */
    private void snapshotItems(Long stocktakeId, StocktakeOrderSaveRequest request) {
        LocalDateTime snapshotAt = LocalDateTime.now();
        if (StocktakeConsts.SCOPE_SKU_SET.equals(request.scopeType())) {
            Map<Long, Integer> onHandBySku = new HashMap<>();
            for (Inventory row : inventoryService.listByWarehouse(request.warehouseId(), request.skuIds())) {
                onHandBySku.put(row.getSkuId(), nvl(row.getQtyOnHand()));
            }
            for (Long skuId : request.skuIds()) {
                stocktakeItemMapper.insert(StocktakeItem.builder()
                        .stocktakeId(stocktakeId)
                        .skuId(skuId)
                        .bookQty(onHandBySku.getOrDefault(skuId, 0))
                        .snapshotAt(snapshotAt)
                        .build());
            }
            return;
        }
        for (Inventory row : inventoryService.listByWarehouse(request.warehouseId(), null)) {
            stocktakeItemMapper.insert(StocktakeItem.builder()
                    .stocktakeId(stocktakeId)
                    .skuId(row.getSkuId())
                    .bookQty(nvl(row.getQtyOnHand()))
                    .snapshotAt(snapshotAt)
                    .build());
        }
    }

    /** 落库前引用与范围校验(建单/改单共用;@Valid 兜 HTTP 侧,此处兜直接调用侧) */
    private void validateRefs(StocktakeOrderSaveRequest request) {
        if (StrUtil.isBlank(request.stocktakeNo())) {
            throw new BusinessException("盘点单号必填");
        }
        if (request.warehouseId() == null) {
            throw new BusinessException("盘点仓必填");
        }
        String scopeType = request.scopeType();
        if (!StocktakeConsts.SCOPE_ALL.equals(scopeType) && !StocktakeConsts.SCOPE_SKU_SET.equals(scopeType)) {
            throw new BusinessException("盘点范围非法(ALL/SKU_SET):" + scopeType);
        }
        if (StocktakeConsts.SCOPE_SKU_SET.equals(scopeType)) {
            if (CollUtil.isEmpty(request.skuIds())) {
                throw new BusinessException("选定 SKU 集盘点时 skuIds 不能为空");
            }
            if (new HashSet<>(request.skuIds()).size() != request.skuIds().size()) {
                throw new BusinessException("盘点 SKU 集存在重复,请去重");
            }
            for (Long skuId : request.skuIds()) {
                if (!goodsSkuApi.existsSku(skuId)) {
                    throw new BusinessException("SKU 不存在:" + skuId);
                }
            }
        }
        if (!warehouseApi.existsWarehouse(request.warehouseId())) {
            throw new BusinessException("仓库不存在:" + request.warehouseId());
        }
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
