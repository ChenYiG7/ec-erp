package com.own.erp.fulfill.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.DeliveryShippedEvent;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.contract.ShopOrderApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.fulfill.constant.DeliveryConsts;
import com.own.erp.fulfill.entity.DeliveryOrder;
import com.own.erp.fulfill.entity.DeliveryOrderItem;
import com.own.erp.fulfill.mapper.DeliveryOrderItemMapper;
import com.own.erp.fulfill.mapper.DeliveryOrderMapper;
import com.own.erp.fulfill.request.command.DeliveryOrderItemSaveRequest;
import com.own.erp.fulfill.request.command.DeliveryOrderSaveRequest;
import com.own.erp.fulfill.request.query.DeliveryOrderQuery;
import com.own.erp.fulfill.response.DeliveryOrderItemResponse;
import com.own.erp.fulfill.response.DeliveryOrderResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 发货单服务:delivery_order(+delivery_order_item 子表)域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     发货单状态机(#11):PENDING(可改/删/取消)→ ship → SHIPPED(库存已动账,禁删改)→ DELIVERED(签收,终态);
 *     CANCELLED 仅 PENDING 可取消(终态,不占订单可发量)。
 *     占用生命周期(#7 2026-09-06 拍板升级:建单即占库存,取代旧"建单不动账"口径)——
 *     save 占 LOCK_SHIP(占用+可用-,缺货建单即拦)→ ship 核销 OUT_SHIP(在库-占用-,可用不变)/
 *     cancel·delete 释放(LOCK_SHIP 负数)/ update 释放旧占+重占新占(行锁串行化与 ship 竞态);
 *     建单前置:订单 WAIT_SHIP + 履约渠道 SELF_FULFILL(FBA/海外仓平台履约不产生系统内发货单,docs/03)+
 *     出库仓存在(WarehouseApi 防幻影库存,同 #10)
 */
@Service
public class DeliveryOrderService {

    private final DeliveryOrderMapper deliveryOrderMapper;
    private final DeliveryOrderItemMapper deliveryOrderItemMapper;
    private final ShopOrderApi shopOrderApi;
    private final WarehouseApi warehouseApi;
    private final InventoryChangeApi inventoryChangeApi;
    private final CurrentUserApi currentUserApi;
    /** 发货完成事件发布(#11 回传编排,2026-09-08):仅发布,消费在 erp-api(事务提交后) */
    private final ApplicationEventPublisher eventPublisher;

    /** 契约接口注入一律 @Lazy 断构造环:实现收口 erp-api 反向注入域 Service,急切装配成环(docs/07 §2.2) */
    public DeliveryOrderService(DeliveryOrderMapper deliveryOrderMapper,
                                DeliveryOrderItemMapper deliveryOrderItemMapper,
                                @Lazy ShopOrderApi shopOrderApi,
                                @Lazy WarehouseApi warehouseApi,
                                @Lazy InventoryChangeApi inventoryChangeApi,
                                @Lazy CurrentUserApi currentUserApi,
                                ApplicationEventPublisher eventPublisher) {
        this.deliveryOrderMapper = deliveryOrderMapper;
        this.deliveryOrderItemMapper = deliveryOrderItemMapper;
        this.shopOrderApi = shopOrderApi;
        this.warehouseApi = warehouseApi;
        this.inventoryChangeApi = inventoryChangeApi;
        this.currentUserApi = currentUserApi;
        this.eventPublisher = eventPublisher;
    }

    /** 分页查询(按 id 倒序;过滤:发货单号模糊/订单/店铺/状态);列表不带明细 */
    public Page<DeliveryOrderResponse> page(DeliveryOrderQuery query) {
        Page<DeliveryOrder> result = deliveryOrderMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<DeliveryOrder>()
                        .like(StrUtil.isNotBlank(query.getDeliveryNo()), DeliveryOrder::getDeliveryNo, query.getDeliveryNo())
                        .eq(query.getOrderId() != null, DeliveryOrder::getOrderId, query.getOrderId())
                        .eq(query.getShopId() != null, DeliveryOrder::getShopId, query.getShopId())
                        .eq(StrUtil.isNotBlank(query.getStatus()), DeliveryOrder::getStatus, query.getStatus())
                        .orderByDesc(DeliveryOrder::getId));
        Page<DeliveryOrderResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(DeliveryOrderResponse::from).toList());
        return responsePage;
    }

    /** 详情带明细(withItems wither 副本);不存在返回 null */
    public DeliveryOrderResponse getById(Long id) {
        DeliveryOrder deliveryOrder = deliveryOrderMapper.selectById(id);
        if (deliveryOrder == null) {
            return null;
        }
        List<DeliveryOrderItemResponse> items = deliveryOrderItemMapper.selectList(
                        new LambdaQueryWrapper<DeliveryOrderItem>()
                                .eq(DeliveryOrderItem::getDeliveryId, id)
                                .orderByAsc(DeliveryOrderItem::getId))
                .stream().map(DeliveryOrderItemResponse::from).toList();
        return DeliveryOrderResponse.from(deliveryOrder).withItems(items);
    }

    /**
     * 创建发货单(待发货,建单即占用库存,#7 2026-09-06 拍板升级,#11):校验订单可发
     * (存在/WAIT_SHIP/SELF_FULFILL)+ 出库仓存在 + 明细行合法(归属/sku_id 已绑定/不重复/剩余量预校验)
     * → PENDING + shop_id 按订单回填 → 落主表与明细 → 逐行经 InventoryChangeApi 占库存
     * (flow_type=LOCK_SHIP 正数:占用+数量、可用-数量,守卫=可用充足,可用不足即建单失败)。
     * 任一步失败整体回滚,单据/明细/占用强一致。单号/运单号撞 uk 捕 DuplicateKeyException 友好报错
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(DeliveryOrderSaveRequest request) {
        ShopOrderApi.OrderDeliveryView view = requireDeliverableOrder(request.orderId());
        if (!warehouseApi.existsWarehouse(request.warehouseId())) {
            throw new BusinessException("出库仓不存在:" + request.warehouseId());
        }
        List<DeliveryOrderItem> lines = assembleLines(request.items(), view, occupiedByOrderItem(view.orderId(), null));
        DeliveryOrder deliveryOrder = request.toEntity();
        // 写前回填服务端管理列(setter 白名单):状态固定待发货,店铺按订单回填,createdBy 按 SecurityContext
        deliveryOrder.setStatus(DeliveryConsts.DELIVERY_PENDING);
        deliveryOrder.setShopId(view.shopId());
        Long operator = currentUserApi.currentUserId();
        deliveryOrder.setCreatedBy(operator);
        try {
            deliveryOrderMapper.insert(deliveryOrder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("发货单号或运单号已存在:" + request.deliveryNo());
        }
        for (DeliveryOrderItem line : lines) {
            line.setDeliveryId(deliveryOrder.getId());
            deliveryOrderItemMapper.insert(line);
        }
        occupyForDelivery(deliveryOrder.getId(), lines, request.warehouseId(),
                "发货单占用:" + deliveryOrder.getDeliveryNo(), operator);
        return deliveryOrder.getId();
    }

    /**
     * 更新:仅 PENDING 可改(待发货单调整明细/物流信息);明细整体替换;不允许变更关联订单。
     * 行锁读(selectByIdForUpdate)串行化与 ship/cancel 的竞态(禁 check-then-act 串状态,docs/07 §6.3):
     * 先改单则 ship 等 commit 后核销新占用,先 ship 则此处行锁读到 SHIPPED 即拒。
     * 同事务释放旧占用(旧仓旧明细 LOCK_SHIP 负数)→ 替换单据与明细 → 重新占用(新仓新明细),
     * 失败整体回滚;数量约束按当前占用(排除自身在途)预校验,原子兜底在占用动账与 ship 核销
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DeliveryOrderSaveRequest request) {
        DeliveryOrder exist = deliveryOrderMapper.selectByIdForUpdate(id);
        if (exist == null) {
            throw new BusinessException("发货单不存在:" + id);
        }
        if (!DeliveryConsts.DELIVERY_PENDING.equals(exist.getStatus())) {
            throw new BusinessException("仅待发货状态可修改,当前:" + exist.getStatus());
        }
        if (!exist.getOrderId().equals(request.orderId())) {
            throw new BusinessException("发货单不允许变更关联订单,请删除重建:" + id);
        }
        ShopOrderApi.OrderDeliveryView view = requireDeliverableOrder(request.orderId());
        if (!warehouseApi.existsWarehouse(request.warehouseId())) {
            throw new BusinessException("出库仓不存在:" + request.warehouseId());
        }
        List<DeliveryOrderItem> lines = assembleLines(request.items(), view,
                occupiedByOrderItem(view.orderId(), id));
        releaseOccupation(exist);
        DeliveryOrder deliveryOrder = request.toEntity();
        deliveryOrder.setId(id);
        try {
            deliveryOrderMapper.updateById(deliveryOrder);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("发货单号或运单号已存在:" + request.deliveryNo());
        }
        deliveryOrderItemMapper.delete(new LambdaQueryWrapper<DeliveryOrderItem>()
                .eq(DeliveryOrderItem::getDeliveryId, id));
        for (DeliveryOrderItem line : lines) {
            line.setDeliveryId(id);
            deliveryOrderItemMapper.insert(line);
        }
        occupyForDelivery(id, lines, request.warehouseId(),
                "发货单占用:" + request.deliveryNo(), exist.getCreatedBy());
    }

    /**
     * 确认发货(#11 核心,同 #10 confirm 先例):PENDING → SHIPPED,同事务完成出库动账与订单推进。
     * ①条件更新占位 SHIPPED(并发双确认/重复确认仅一个成功,affected=0 拒;失败由事务整体回滚);
     * ②逐行库存变更(唯一入口 InventoryService.change,flow_type=OUT_SHIP 数量为负 = 出库核销占用:
     * 在库-数量、占用-数量,可用不变——建单时已占,守卫=在库/占用充足,biz 指向本发货单);
     * ③回写 shipped_at;
     * ④发足判定:按 order_item_id 聚合该订单全部非 CANCELLED 发货单明细,仅 sku_id 已绑定行全部发足时
     * casOrderStatus 推进 WAIT_SHIP→SHIPPED(未命中不报错——部分发货/订单已被拉单推进都属正常);
     * ⑤发布 DeliveryShippedEvent(erp-common):仅"事件发布",回传平台编排在 erp-api(ShipmentSyncService)
     * 以 AFTER_COMMIT 相位消费——本域不具备 ShopSession/AdapterRegistry,且回传失败不得回滚本地发货
     * (docs/04 回传拍板:失败记 pull_log,平台侧可手工补);事件在事务内发布,提交后才触发
     */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id) {
        DeliveryOrder delivery = deliveryOrderMapper.selectById(id);
        if (delivery == null) {
            throw new BusinessException("发货单不存在:" + id);
        }
        if (deliveryOrderMapper.casStatus(id, DeliveryConsts.DELIVERY_PENDING, DeliveryConsts.DELIVERY_SHIPPED) == 0) {
            throw new BusinessException("确认失败:发货单不存在或不是待发货状态");
        }
        List<DeliveryOrderItem> lines = deliveryOrderItemMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrderItem>().eq(DeliveryOrderItem::getDeliveryId, id));
        if (CollUtil.isEmpty(lines)) {
            throw new BusinessException("发货单无明细,禁止确认:" + id);
        }
        for (DeliveryOrderItem line : lines) {
            inventoryChangeApi.change(InventoryChangeCommand.builder()
                    .skuId(line.getSkuId())
                    .warehouseId(delivery.getWarehouseId())
                    .quantity(-line.getShipQty())
                    .flowType(InventoryConsts.FLOW_TYPE_OUT_SHIP)
                    .bizType(DeliveryConsts.BIZ_TYPE_DELIVERY_ORDER)
                    .bizId(id)
                    .remark("发货单:" + delivery.getDeliveryNo())
                    .createdBy(delivery.getCreatedBy())
                    .build());
        }
        DeliveryOrder mark = DeliveryOrder.builder().id(id).shippedAt(LocalDateTime.now()).build();
        deliveryOrderMapper.updateById(mark);
        advanceOrderIfFullyShipped(delivery.getOrderId());
        eventPublisher.publishEvent(new DeliveryShippedEvent(id, delivery.getOrderId(), delivery.getShopId()));
    }

    /**
     * 取消(复合事务动作,#7 2026-09-06 占用释放):仅 PENDING → CANCELLED 条件更新占位
     * (并发双取消/取消与 ship 竞态仅一个成功)→ 释放建单占用(逐行 LOCK_SHIP 负数,可用回补);
     * 失败整体回滚,状态/占用强一致。已发货库存已核销出库,走不了取消
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        if (deliveryOrderMapper.casStatus(id, DeliveryConsts.DELIVERY_PENDING, DeliveryConsts.DELIVERY_CANCELLED) == 0) {
            throw new BusinessException("取消失败:发货单不存在或不是待发货状态");
        }
        releaseOccupation(deliveryOrderMapper.selectById(id));
    }

    /** 标记签收:SHIPPED → DELIVERED(一期人工签收;平台物流轨迹自动签收随 #3 adapter 回传评估) */
    public void markDelivered(Long id) {
        if (deliveryOrderMapper.casStatus(id, DeliveryConsts.DELIVERY_SHIPPED, DeliveryConsts.DELIVERY_DELIVERED) == 0) {
            throw new BusinessException("签收失败:发货单不存在或不是已发货状态");
        }
    }

    /**
     * 删除:SHIPPED/DELIVERED 禁删(库存已核销出库,删单致账实无法追溯);
     * PENDING 先 cas→CANCELLED 占位(防与 ship/cancel 并发,占位后本事务行锁在手)再释放占用;
     * CANCELLED 占用在取消时已释放,不重复释放。PENDING/CANCELLED 连明细同事务删
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DeliveryOrder exist = deliveryOrderMapper.selectById(id);
        if (exist == null) {
            return;
        }
        if (DeliveryConsts.DELIVERY_SHIPPED.equals(exist.getStatus())
                || DeliveryConsts.DELIVERY_DELIVERED.equals(exist.getStatus())) {
            throw new BusinessException("已发货单据禁止删除(库存已动账):" + id);
        }
        boolean pending = DeliveryConsts.DELIVERY_PENDING.equals(exist.getStatus());
        if (pending && deliveryOrderMapper.casStatus(id, DeliveryConsts.DELIVERY_PENDING,
                DeliveryConsts.DELIVERY_CANCELLED) == 0) {
            throw new BusinessException("删除失败:发货单状态已变化(可能已确认发货),请刷新重试:" + id);
        }
        if (pending) {
            releaseOccupation(exist);
        }
        deliveryOrderItemMapper.delete(new LambdaQueryWrapper<DeliveryOrderItem>()
                .eq(DeliveryOrderItem::getDeliveryId, id));
        deliveryOrderMapper.deleteById(id);
    }

    /**
     * 逐行占库存(建单/改单重占,#7):LOCK_SHIP 正数——占用+数量、可用-数量,
     * 守卫=可用充足(缺货在占用时即拦,不再等到发货);经唯一入口 InventoryChangeApi(铁律 4)
     */
    private void occupyForDelivery(Long deliveryId, List<DeliveryOrderItem> lines, Long warehouseId,
                                   String remark, Long operator) {
        for (DeliveryOrderItem line : lines) {
            inventoryChangeApi.change(InventoryChangeCommand.builder()
                    .skuId(line.getSkuId())
                    .warehouseId(warehouseId)
                    .quantity(line.getShipQty())
                    .flowType(InventoryConsts.FLOW_TYPE_LOCK_SHIP)
                    .bizType(DeliveryConsts.BIZ_TYPE_DELIVERY_ORDER)
                    .bizId(deliveryId)
                    .remark(remark)
                    .createdBy(operator)
                    .build());
        }
    }

    /**
     * 释放建单占用(cancel/update 重占前/delete,同事务调用):按单据存量明细逐行 LOCK_SHIP 负数
     * (数量与建单占用镜像,仓库取单据出库仓);行不存在/守卫不足由 change() 拒绝并随事务回滚
     */
    private void releaseOccupation(DeliveryOrder delivery) {
        for (DeliveryOrderItem line : deliveryOrderItemMapper.selectList(
                new LambdaQueryWrapper<DeliveryOrderItem>()
                        .eq(DeliveryOrderItem::getDeliveryId, delivery.getId()))) {
            inventoryChangeApi.change(InventoryChangeCommand.builder()
                    .skuId(line.getSkuId())
                    .warehouseId(delivery.getWarehouseId())
                    .quantity(-line.getShipQty())
                    .flowType(InventoryConsts.FLOW_TYPE_LOCK_SHIP)
                    .bizType(DeliveryConsts.BIZ_TYPE_DELIVERY_ORDER)
                    .bizId(delivery.getId())
                    .remark("发货单释放:" + delivery.getDeliveryNo())
                    .createdBy(delivery.getCreatedBy())
                    .build());
        }
    }

    /** 订单可发校验(#11 建单/改单前置):存在 + WAIT_SHIP + SELF_FULFILL,返回发货视图 */
    private ShopOrderApi.OrderDeliveryView requireDeliverableOrder(Long orderId) {
        ShopOrderApi.OrderDeliveryView view = shopOrderApi.findDeliveryView(orderId);
        if (view == null) {
            throw new BusinessException("订单不存在:" + orderId);
        }
        if (!DeliveryConsts.ORDER_WAIT_SHIP.equals(view.orderStatus())) {
            throw new BusinessException("仅待发货订单可创建发货单,当前订单状态:" + view.orderStatus());
        }
        if (!DeliveryConsts.CHANNEL_SELF_FULFILL.equals(view.fulfillmentChannel())) {
            throw new BusinessException("仅卖家自履约订单可创建发货单(FBA/海外仓由平台/仓履约),当前:" + view.fulfillmentChannel());
        }
        return view;
    }

    /** 校验并装配发货明细行:订单明细归属 + sku_id 已绑定 + 行去重 + 剩余量预校验;skuId 服务端回填 */
    private List<DeliveryOrderItem> assembleLines(List<DeliveryOrderItemSaveRequest> requests,
                                                  ShopOrderApi.OrderDeliveryView view,
                                                  Map<Long, Integer> occupiedByOrderItem) {
        if (CollUtil.isEmpty(requests)) {
            throw new BusinessException("发货明细不能为空");
        }
        Map<Long, ShopOrderApi.OrderDeliveryView.Item> itemById = new HashMap<>();
        for (ShopOrderApi.OrderDeliveryView.Item item : view.items()) {
            itemById.putIfAbsent(item.orderItemId(), item);
        }
        HashSet<Long> seenOrderItemIds = new HashSet<>();
        return requests.stream().map(request -> {
            ShopOrderApi.OrderDeliveryView.Item item = itemById.get(request.orderItemId());
            if (item == null) {
                throw new BusinessException("发货明细不属于该订单或SKU未绑定:orderItemId=" + request.orderItemId());
            }
            if (!seenOrderItemIds.add(request.orderItemId())) {
                throw new BusinessException("同一订单明细在发货单内重复,请合并为一行:orderItemId=" + request.orderItemId());
            }
            int remaining = nvl(item.quantity()) - occupiedByOrderItem.getOrDefault(request.orderItemId(), 0);
            if (request.shipQty() > remaining) {
                throw new BusinessException("发货数量超出剩余可发量:orderItemId=" + request.orderItemId()
                        + ",剩余" + remaining);
            }
            // builder 纯构造装配(docs/07 §1 分级⑤);skuId 从订单明细回填,不收客户端值
            return DeliveryOrderItem.builder()
                    .orderItemId(item.orderItemId())
                    .skuId(item.skuId())
                    .shipQty(request.shipQty())
                    .build();
        }).toList();
    }

    /**
     * 订单明细发货占用聚合(建单/改单剩余量预校验用):按 order_item_id 汇总该订单全部
     * 非 CANCELLED 发货单(PENDING 在途 + SHIPPED/DELIVERED 已发)的明细数量——
     * 把 PENDING 在途也算上是防建单阶段超配;excludeDeliveryId 非空排除自身(改单场景)。
     * 逐单 eq 查而不用 in 聚合:Lambda wrapper 的 in 急切解析列元数据,纯 Mockito 单测不可直测(docs/07);
     * 单订单发货单个位数,开销可忽略。并发建单窗口(两单同时过校验)一期人工操作低频接受,
     * 超发最终被 ship 的库存动账余额校验兜底
     */
    private Map<Long, Integer> occupiedByOrderItem(Long orderId, Long excludeDeliveryId) {
        List<DeliveryOrder> deliveries = deliveryOrderMapper.selectList(new LambdaQueryWrapper<DeliveryOrder>()
                .eq(DeliveryOrder::getOrderId, orderId)
                .ne(DeliveryOrder::getStatus, DeliveryConsts.DELIVERY_CANCELLED)
                .ne(excludeDeliveryId != null, DeliveryOrder::getId, excludeDeliveryId));
        Map<Long, Integer> occupied = new HashMap<>();
        for (DeliveryOrder delivery : deliveries) {
            for (DeliveryOrderItem line : deliveryOrderItemMapper.selectList(
                    new LambdaQueryWrapper<DeliveryOrderItem>()
                            .eq(DeliveryOrderItem::getDeliveryId, delivery.getId()))) {
                occupied.merge(line.getOrderItemId(), nvl(line.getShipQty()), Integer::sum);
            }
        }
        return occupied;
    }

    /** 发足判定并推进订单状态:全部已绑定订单明细行(Σ 非 CANCELLED 发货明细 ≥ quantity)满足才推进 */
    private void advanceOrderIfFullyShipped(Long orderId) {
        ShopOrderApi.OrderDeliveryView view = shopOrderApi.findDeliveryView(orderId);
        if (view == null || CollUtil.isEmpty(view.items())) {
            return;
        }
        Map<Long, Integer> shipped = occupiedByOrderItem(orderId, null);
        for (ShopOrderApi.OrderDeliveryView.Item item : view.items()) {
            if (shipped.getOrDefault(item.orderItemId(), 0) < nvl(item.quantity())) {
                return;
            }
        }
        shopOrderApi.casOrderStatus(orderId, DeliveryConsts.ORDER_WAIT_SHIP, DeliveryConsts.ORDER_SHIPPED);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
