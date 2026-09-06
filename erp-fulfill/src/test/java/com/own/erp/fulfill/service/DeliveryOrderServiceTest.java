package com.own.erp.fulfill.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.ShopOrderApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.fulfill.constant.DeliveryConsts;
import com.own.erp.fulfill.entity.DeliveryOrder;
import com.own.erp.fulfill.entity.DeliveryOrderItem;
import com.own.erp.fulfill.mapper.DeliveryOrderItemMapper;
import com.own.erp.fulfill.mapper.DeliveryOrderMapper;
import com.own.erp.fulfill.request.command.DeliveryOrderItemSaveRequest;
import com.own.erp.fulfill.request.command.DeliveryOrderSaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : DeliveryOrderService #11 激活单测(AIR:mock Mapper 与契约接口,不依赖数据库):
 *     建单校验链(订单可发/仓库存在/明细归属与剩余量)/ 建单占用与取消·删除释放(#7 2026-09-06)/
 *     ship 守卫与出库动账/发足推进订单状态/ cancel·deliver·delete 守卫/ 改单释放重占。
 *     注:occupiedByOrderItem/明细装配内部用 Lambda wrapper .ne(cond,...) 条件重载与 .in(),
 *     纯 Mockito 环境可构造(懒解析),Mapper 一律 any() 打桩
 */
class DeliveryOrderServiceTest {

    private static final long ORDER_ID = 100L;
    private static final long WAREHOUSE_ID = 5L;

    private DeliveryOrderMapper deliveryOrderMapper;
    private DeliveryOrderItemMapper deliveryOrderItemMapper;
    private ShopOrderApi shopOrderApi;
    private WarehouseApi warehouseApi;
    private InventoryChangeApi inventoryChangeApi;
    private CurrentUserApi currentUserApi;
    private DeliveryOrderService deliveryOrderService;

    @BeforeEach
    void setUp() {
        deliveryOrderMapper = mock(DeliveryOrderMapper.class);
        deliveryOrderItemMapper = mock(DeliveryOrderItemMapper.class);
        shopOrderApi = mock(ShopOrderApi.class);
        warehouseApi = mock(WarehouseApi.class);
        inventoryChangeApi = mock(InventoryChangeApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        deliveryOrderService = new DeliveryOrderService(deliveryOrderMapper, deliveryOrderItemMapper,
                shopOrderApi, warehouseApi, inventoryChangeApi, currentUserApi);
        // createdBy 服务端按 SecurityContext 回填(CurrentUserApi,#11 遗留收口)
        when(currentUserApi.currentUserId()).thenReturn(9L);
    }

    // ---------- 造数 ----------

    private ShopOrderApi.OrderDeliveryView.Item viewItem(long orderItemId, long skuId, int quantity) {
        return ShopOrderApi.OrderDeliveryView.Item.builder()
                .orderItemId(orderItemId).skuId(skuId).quantity(quantity).build();
    }

    private ShopOrderApi.OrderDeliveryView view(String status, String channel,
                                                List<ShopOrderApi.OrderDeliveryView.Item> items) {
        return ShopOrderApi.OrderDeliveryView.builder()
                .orderId(ORDER_ID).shopId(2L).orderStatus(status).fulfillmentChannel(channel).items(items).build();
    }

    private DeliveryOrderSaveRequest request(DeliveryOrderItemSaveRequest... items) {
        return DeliveryOrderSaveRequest.builder()
                .deliveryNo("D001")
                .orderId(ORDER_ID)
                .warehouseId(WAREHOUSE_ID)
                .type("MANUAL")
                .items(List.of(items))
                .build();
    }

    private DeliveryOrderItemSaveRequest line(long orderItemId, int shipQty) {
        return DeliveryOrderItemSaveRequest.builder().orderItemId(orderItemId).shipQty(shipQty).build();
    }

    /** 订单可发 + 仓库存在的公共打桩 */
    private void stubDeliverableOrder() {
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(
                view("WAIT_SHIP", "SELF_FULFILL", List.of(viewItem(11L, 1001L, 10))));
        when(warehouseApi.existsWarehouse(WAREHOUSE_ID)).thenReturn(true);
    }

    // ---------- save 建单校验链 ----------

    @Test
    void saveRejectedWhenOrderMissing() {
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request(line(11L, 1))));

        assertTrue(e.getMessage().contains("订单不存在"));
        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    void saveRejectedWhenOrderNotWaitShip() {
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(
                view("WAIT_PAY", "SELF_FULFILL", List.of(viewItem(11L, 1001L, 10))));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request(line(11L, 1))));

        assertTrue(e.getMessage().contains("仅待发货订单"));
        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    void saveRejectedWhenFbaOrder() {
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(
                view("WAIT_SHIP", "FBA", List.of(viewItem(11L, 1001L, 10))));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request(line(11L, 1))));

        assertTrue(e.getMessage().contains("卖家自履约"));
        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    void saveRejectedWhenWarehouseMissing() {
        stubDeliverableOrder();
        when(warehouseApi.existsWarehouse(WAREHOUSE_ID)).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request(line(11L, 1))));

        assertTrue(e.getMessage().contains("出库仓不存在"));
        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    void saveRejectedWhenItemsEmpty() {
        stubDeliverableOrder();

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request()));

        assertTrue(e.getMessage().contains("明细不能为空"));
        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    void saveRejectedWhenOrderItemNotInOrderOrUnboundSku() {
        stubDeliverableOrder();

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request(line(99L, 1))));

        assertTrue(e.getMessage().contains("不属于该订单或SKU未绑定"));
        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    void saveRejectedWhenSameOrderItemDuplicatedInRequest() {
        stubDeliverableOrder();

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request(line(11L, 1), line(11L, 2))));

        assertTrue(e.getMessage().contains("重复"));
        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    void saveRejectedWhenOverShippedByPendingDeliveries() {
        stubDeliverableOrder();
        // 存量在途单已占 10(=上限),再发 1 即超
        DeliveryOrder pending = DeliveryOrder.builder().id(77L).orderId(ORDER_ID).status("PENDING").build();
        when(deliveryOrderMapper.selectList(any())).thenReturn(List.of(pending));
        when(deliveryOrderItemMapper.selectList(any())).thenReturn(List.of(
                DeliveryOrderItem.builder().deliveryId(77L).orderItemId(11L).skuId(1001L).shipQty(10).build()));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request(line(11L, 1))));

        assertTrue(e.getMessage().contains("剩余可发量"));
        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    void saveFillsServerManagedColumnsAndBackfillsSkuId() {
        stubDeliverableOrder();

        deliveryOrderService.save(request(line(11L, 4)));

        ArgumentCaptor<DeliveryOrder> orderCaptor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper).insert(orderCaptor.capture());
        assertEquals("PENDING", orderCaptor.getValue().getStatus());
        assertEquals(2L, orderCaptor.getValue().getShopId());
        assertEquals(WAREHOUSE_ID, orderCaptor.getValue().getWarehouseId());
        // createdBy 服务端按 SecurityContext 回填,不收客户端值
        assertEquals(9L, orderCaptor.getValue().getCreatedBy());

        ArgumentCaptor<DeliveryOrderItem> lineCaptor = ArgumentCaptor.forClass(DeliveryOrderItem.class);
        verify(deliveryOrderItemMapper).insert(lineCaptor.capture());
        assertEquals(1001L, lineCaptor.getValue().getSkuId());
        assertEquals(4, lineCaptor.getValue().getShipQty());
    }

    @Test
    void saveConvertsDuplicateKeyToBusinessException() {
        stubDeliverableOrder();
        when(deliveryOrderMapper.insert(any(DeliveryOrder.class))).thenThrow(new DuplicateKeyException("uk_delivery_no"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.save(request(line(11L, 1))));

        assertTrue(e.getMessage().contains("已存在"));
        verify(inventoryChangeApi, never()).change(any());
    }

    @Test
    void saveLocksInventoryPerLine() {
        // 建单即占库存(#7):逐行 LOCK_SHIP 正数,占用+数量/可用-数量,缺货建单即拦
        stubDeliverableOrder();
        when(deliveryOrderMapper.insert(any(DeliveryOrder.class))).thenAnswer(inv -> {
            inv.getArgument(0, DeliveryOrder.class).setId(88L);
            return 1;
        });

        deliveryOrderService.save(request(line(11L, 4)));

        ArgumentCaptor<InventoryChangeCommand> captor = ArgumentCaptor.forClass(InventoryChangeCommand.class);
        verify(inventoryChangeApi).change(captor.capture());
        InventoryChangeCommand lock = captor.getValue();
        assertEquals(1001L, lock.skuId());
        assertEquals(WAREHOUSE_ID, lock.warehouseId());
        assertEquals(4, lock.quantity());
        assertEquals("LOCK_SHIP", lock.flowType());
        assertEquals("DELIVERY_ORDER", lock.bizType());
        assertEquals(88L, lock.bizId());
        assertEquals(9L, lock.createdBy());
    }

    @Test
    void savePropagatesLockShortageFromInventory() {
        // 可用不足:占用动账抛业务异常,单据不落(事务回滚语义由 @Transactional 保证,此处验传播)
        stubDeliverableOrder();
        when(inventoryChangeApi.change(any())).thenThrow(new BusinessException("可用库存不足:当前0,变动4"));

        assertThrows(BusinessException.class, () -> deliveryOrderService.save(request(line(11L, 4))));
    }

    // ---------- ship 确认发货 ----------

    /** 组装一张已落库的待发货单 + 两行明细(发足场景) */
    private void stubShippableDelivery() {
        DeliveryOrder delivery = DeliveryOrder.builder()
                .id(1L).deliveryNo("D001").orderId(ORDER_ID).shopId(2L).warehouseId(WAREHOUSE_ID)
                .status("PENDING").createdBy(9L).build();
        when(deliveryOrderMapper.selectById(1L)).thenReturn(delivery);
        when(deliveryOrderMapper.casStatus(1L, "PENDING", "SHIPPED")).thenReturn(1);
        List<DeliveryOrderItem> lines = List.of(
                DeliveryOrderItem.builder().id(101L).deliveryId(1L).orderItemId(11L).skuId(1001L).shipQty(6).build(),
                DeliveryOrderItem.builder().id(102L).deliveryId(1L).orderItemId(12L).skuId(1002L).shipQty(4).build());
        when(deliveryOrderItemMapper.selectList(any())).thenReturn(lines);
        // 发足判定时重查订单维度发货占用(本单已转 SHIPPED,一并计入)
        when(deliveryOrderMapper.selectList(any())).thenReturn(List.of(
                DeliveryOrder.builder().id(1L).orderId(ORDER_ID).status("SHIPPED").build()));
        when(deliveryOrderItemMapper.selectList(any())).thenReturn(lines);
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("WAIT_SHIP", "SELF_FULFILL", List.of(
                viewItem(11L, 1001L, 6), viewItem(12L, 1002L, 4))));
    }

    @Test
    void shipRejectedWhenCasStatusMisses() {
        DeliveryOrder delivery = DeliveryOrder.builder().id(1L).status("SHIPPED").build();
        when(deliveryOrderMapper.selectById(1L)).thenReturn(delivery);
        when(deliveryOrderMapper.casStatus(1L, "PENDING", "SHIPPED")).thenReturn(0);

        assertThrows(BusinessException.class, () -> deliveryOrderService.ship(1L));

        verify(inventoryChangeApi, never()).change(any());
    }

    @Test
    void shipRejectedWhenNoLines() {
        DeliveryOrder delivery = DeliveryOrder.builder().id(1L).status("PENDING").build();
        when(deliveryOrderMapper.selectById(1L)).thenReturn(delivery);
        when(deliveryOrderMapper.casStatus(1L, "PENDING", "SHIPPED")).thenReturn(1);
        when(deliveryOrderItemMapper.selectList(any())).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> deliveryOrderService.ship(1L));

        verify(inventoryChangeApi, never()).change(any());
    }

    @Test
    void shipWritesOutboundInventoryAndAdvancesOrderWhenFullyShipped() {
        stubShippableDelivery();

        deliveryOrderService.ship(1L);

        ArgumentCaptor<InventoryChangeCommand> captor = ArgumentCaptor.forClass(InventoryChangeCommand.class);
        verify(inventoryChangeApi, times(2)).change(captor.capture());
        InventoryChangeCommand first = captor.getAllValues().get(0);
        assertEquals(1001L, first.skuId());
        assertEquals(WAREHOUSE_ID, first.warehouseId());
        assertEquals(-6, first.quantity());
        assertEquals("OUT_SHIP", first.flowType());
        assertEquals("DELIVERY_ORDER", first.bizType());
        assertEquals(1L, first.bizId());
        assertEquals(9L, first.createdBy());
        // 回写发货时间
        verify(deliveryOrderMapper).updateById(any(DeliveryOrder.class));
        // 全部明细发足 → 推进订单
        verify(shopOrderApi).casOrderStatus(ORDER_ID, "WAIT_SHIP", "SHIPPED");
    }

    @Test
    void shipSkipsOrderAdvanceWhenPartiallyShipped() {
        stubShippableDelivery();
        // 第二行只发 2/4 → 未发足,不推进
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("WAIT_SHIP", "SELF_FULFILL", List.of(
                viewItem(11L, 1001L, 6), viewItem(12L, 1002L, 4))));
        when(deliveryOrderItemMapper.selectList(any())).thenReturn(List.of(
                DeliveryOrderItem.builder().deliveryId(1L).orderItemId(11L).skuId(1001L).shipQty(6).build(),
                DeliveryOrderItem.builder().deliveryId(1L).orderItemId(12L).skuId(1002L).shipQty(2).build()));

        deliveryOrderService.ship(1L);

        verify(inventoryChangeApi, times(2)).change(any());
        verify(shopOrderApi, never()).casOrderStatus(any(), any(), any());
    }

    @Test
    void shipToleratesOrderViewMissingWhenAdvancing() {
        stubShippableDelivery();
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(null);

        deliveryOrderService.ship(1L);

        verify(inventoryChangeApi, times(2)).change(any());
        verify(shopOrderApi, never()).casOrderStatus(any(), any(), any());
    }

    // ---------- cancel / deliver / delete 守卫 ----------

    @Test
    void cancelRejectedWhenNotPending() {
        when(deliveryOrderMapper.casStatus(1L, "PENDING", "CANCELLED")).thenReturn(0);

        assertThrows(BusinessException.class, () -> deliveryOrderService.cancel(1L));
        verify(inventoryChangeApi, never()).change(any());
    }

    @Test
    void cancelReleasesOccupationWhenPending() {
        // 取消释放(#7):cas 占位后按存量明细逐行 LOCK_SHIP 负数,仓库取单据出库仓
        when(deliveryOrderMapper.casStatus(1L, "PENDING", "CANCELLED")).thenReturn(1);
        when(deliveryOrderMapper.selectById(1L)).thenReturn(DeliveryOrder.builder()
                .id(1L).deliveryNo("D001").warehouseId(WAREHOUSE_ID).createdBy(9L).status("CANCELLED").build());
        when(deliveryOrderItemMapper.selectList(any())).thenReturn(List.of(
                DeliveryOrderItem.builder().deliveryId(1L).orderItemId(11L).skuId(1001L).shipQty(4).build()));

        deliveryOrderService.cancel(1L);

        ArgumentCaptor<InventoryChangeCommand> captor = ArgumentCaptor.forClass(InventoryChangeCommand.class);
        verify(inventoryChangeApi).change(captor.capture());
        assertEquals(-4, captor.getValue().quantity());
        assertEquals(WAREHOUSE_ID, captor.getValue().warehouseId());
        assertEquals("LOCK_SHIP", captor.getValue().flowType());
    }

    @Test
    void deliverRejectedWhenNotShipped() {
        when(deliveryOrderMapper.casStatus(1L, "SHIPPED", "DELIVERED")).thenReturn(0);

        assertThrows(BusinessException.class, () -> deliveryOrderService.markDelivered(1L));
    }

    @Test
    void deleteRejectedWhenInventoryAlreadyMoved() {
        when(deliveryOrderMapper.selectById(1L)).thenReturn(
                DeliveryOrder.builder().id(1L).status("SHIPPED").build());

        BusinessException e = assertThrows(BusinessException.class, () -> deliveryOrderService.delete(1L));

        assertTrue(e.getMessage().contains("禁止删除"));
        verify(deliveryOrderMapper, never()).deleteById(1L);
    }

    @Test
    void deleteReleasesOccupationWhenPending() {
        when(deliveryOrderMapper.selectById(1L)).thenReturn(
                DeliveryOrder.builder().id(1L).deliveryNo("D001").warehouseId(WAREHOUSE_ID).status("PENDING").build());
        when(deliveryOrderMapper.casStatus(1L, "PENDING", "CANCELLED")).thenReturn(1);
        when(deliveryOrderItemMapper.selectList(any())).thenReturn(List.of(
                DeliveryOrderItem.builder().deliveryId(1L).orderItemId(11L).skuId(1001L).shipQty(4).build()));

        deliveryOrderService.delete(1L);

        // 先 cas 占位防 ship 竞态,再释放占用,最后删行
        verify(deliveryOrderMapper).casStatus(1L, "PENDING", "CANCELLED");
        ArgumentCaptor<InventoryChangeCommand> captor = ArgumentCaptor.forClass(InventoryChangeCommand.class);
        verify(inventoryChangeApi).change(captor.capture());
        assertEquals(-4, captor.getValue().quantity());
        verify(deliveryOrderItemMapper).delete(any());
        verify(deliveryOrderMapper).deleteById(1L);
    }

    @Test
    void deleteSkipsReleaseWhenAlreadyCancelled() {
        // CANCELLED 的占用已在取消时释放,删除不得重复释放
        when(deliveryOrderMapper.selectById(1L)).thenReturn(
                DeliveryOrder.builder().id(1L).warehouseId(WAREHOUSE_ID).status("CANCELLED").build());

        deliveryOrderService.delete(1L);

        verify(deliveryOrderMapper, never()).casStatus(any(), any(), any());
        verify(inventoryChangeApi, never()).change(any());
        verify(deliveryOrderMapper).deleteById(1L);
    }

    // ---------- update 守卫 ----------

    @Test
    void updateRejectedWhenAlreadyShipped() {
        when(deliveryOrderMapper.selectByIdForUpdate(1L)).thenReturn(
                DeliveryOrder.builder().id(1L).orderId(ORDER_ID).status("SHIPPED").build());

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.update(1L, request(line(11L, 1))));

        assertTrue(e.getMessage().contains("仅待发货状态可修改"));
        verify(deliveryOrderMapper, never()).updateById(any(DeliveryOrder.class));
    }

    @Test
    void updateRejectedWhenSwitchingOrder() {
        when(deliveryOrderMapper.selectByIdForUpdate(1L)).thenReturn(
                DeliveryOrder.builder().id(1L).orderId(ORDER_ID).status("PENDING").build());

        // 换绑订单:构造 orderId 不同的请求
        DeliveryOrderSaveRequest switched = DeliveryOrderSaveRequest.builder()
                .deliveryNo("D001").orderId(999L).warehouseId(WAREHOUSE_ID).items(List.of(line(11L, 1))).build();

        BusinessException e = assertThrows(BusinessException.class,
                () -> deliveryOrderService.update(1L, switched));

        assertTrue(e.getMessage().contains("不允许变更关联订单"));
        verify(deliveryOrderMapper, never()).updateById(any(DeliveryOrder.class));
    }

    @Test
    void updateReplacesLinesWithReleaseAndReoccupy() {
        stubDeliverableOrder();
        when(deliveryOrderMapper.selectByIdForUpdate(1L)).thenReturn(DeliveryOrder.builder()
                .id(1L).orderId(ORDER_ID).deliveryNo("D001").warehouseId(WAREHOUSE_ID).status("PENDING").build());
        // 存量占用明细(旧锁):shipQty 4
        when(deliveryOrderItemMapper.selectList(any())).thenReturn(List.of(
                DeliveryOrderItem.builder().deliveryId(1L).orderItemId(11L).skuId(1001L).shipQty(4).build()));

        deliveryOrderService.update(1L, request(line(11L, 3)));

        // 释放旧占用(−4)→ 替换 → 重新占用(+3),同事务失败回滚
        ArgumentCaptor<InventoryChangeCommand> captor = ArgumentCaptor.forClass(InventoryChangeCommand.class);
        verify(inventoryChangeApi, times(2)).change(captor.capture());
        assertEquals(-4, captor.getAllValues().get(0).quantity());
        assertEquals(3, captor.getAllValues().get(1).quantity());
        verify(deliveryOrderMapper).updateById(any(DeliveryOrder.class));
        verify(deliveryOrderItemMapper).delete(any());
        verify(deliveryOrderItemMapper).insert(any(DeliveryOrderItem.class));
    }

    @Test
    void updateRejectedWhenShippedWinsRaceBeforeRowLock() {
        // 行锁读到 SHIPPED(ship 先行)= 拒改,不释放已核销单据的占用
        when(deliveryOrderMapper.selectByIdForUpdate(1L)).thenReturn(
                DeliveryOrder.builder().id(1L).orderId(ORDER_ID).status("SHIPPED").build());

        assertThrows(BusinessException.class, () -> deliveryOrderService.update(1L, request(line(11L, 1))));
        verify(inventoryChangeApi, never()).change(any());
    }
}
