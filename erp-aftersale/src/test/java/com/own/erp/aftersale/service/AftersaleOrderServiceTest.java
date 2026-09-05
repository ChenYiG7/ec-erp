package com.own.erp.aftersale.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.aftersale.constant.AftersaleConsts;
import com.own.erp.aftersale.entity.AftersaleOrder;
import com.own.erp.aftersale.entity.AftersaleReturnItem;
import com.own.erp.aftersale.mapper.AftersaleOrderMapper;
import com.own.erp.aftersale.mapper.AftersaleReturnItemMapper;
import com.own.erp.aftersale.request.command.AftersaleReturnItemRequest;
import com.own.erp.aftersale.request.command.AftersaleReturnReceiveRequest;
import com.own.erp.aftersale.request.query.AftersaleOrderQuery;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.InventoryChangeApi;
import com.own.erp.contract.InventoryChangeCommand;
import com.own.erp.contract.InventoryConsts;
import com.own.erp.contract.ShopOrderApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.platform.unified.UnifiedRefund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : AftersaleOrderService 单测(AIR:mock Mapper/契约接口,不依赖数据库)
 *     状态机组(#12):条件更新即守卫,affected=0 / 单不存在 / result 缺失均须拒且不落库;
 *     收退件复合动作组(#12 退货入库):校验链(仓/明细/归属/数量)前置,占位命中才动账+落明细
 */
class AftersaleOrderServiceTest {

    private static final Long ID = 1L;

    private AftersaleOrderMapper aftersaleOrderMapper;
    private AftersaleReturnItemMapper aftersaleReturnItemMapper;
    private ShopOrderApi shopOrderApi;
    private WarehouseApi warehouseApi;
    private InventoryChangeApi inventoryChangeApi;
    private AftersaleOrderService aftersaleOrderService;

    @BeforeEach
    void setUp() {
        aftersaleOrderMapper = mock(AftersaleOrderMapper.class);
        aftersaleReturnItemMapper = mock(AftersaleReturnItemMapper.class);
        shopOrderApi = mock(ShopOrderApi.class);
        warehouseApi = mock(WarehouseApi.class);
        inventoryChangeApi = mock(InventoryChangeApi.class);
        aftersaleOrderService = new AftersaleOrderService(aftersaleOrderMapper, aftersaleReturnItemMapper,
                shopOrderApi, warehouseApi, inventoryChangeApi);
    }

    private AftersaleOrder order(String type) {
        return AftersaleOrder.builder().id(ID).aftersaleNo("AS001").type(type).build();
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        AftersaleOrder aftersaleOrder = new AftersaleOrder();
        aftersaleOrder.setId(1L);
        when(aftersaleOrderMapper.selectById(1L)).thenReturn(aftersaleOrder);
        assertEquals(1L, aftersaleOrderService.getById(1L).id());
        assertNull(aftersaleOrderService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        AftersaleOrder aftersaleOrder = new AftersaleOrder();
        aftersaleOrder.setId(2L);
        Page<AftersaleOrder> page = new Page<>(1, 10);
        page.setRecords(List.of(aftersaleOrder));
        doReturn(page).when(aftersaleOrderMapper).selectPage(any(), any());
        assertEquals(2L, aftersaleOrderService.page(new AftersaleOrderQuery()).getRecords().get(0).id());
    }

    @Nested
    class StateMachine {

        @Test
        void agreeRoutesDirectRefundTypesToApproved() {
            when(aftersaleOrderMapper.selectById(ID)).thenReturn(order(AftersaleConsts.TYPE_REFUND_ONLY));
            when(aftersaleOrderMapper.casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_APPROVED, "凭证齐全")).thenReturn(1);
            aftersaleOrderService.agree(ID, "凭证齐全");
            verify(aftersaleOrderMapper).casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_APPROVED, "凭证齐全");

            when(aftersaleOrderMapper.selectById(ID)).thenReturn(order(AftersaleConsts.TYPE_RESEND));
            when(aftersaleOrderMapper.casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_APPROVED, null)).thenReturn(1);
            aftersaleOrderService.agree(ID, null);
            verify(aftersaleOrderMapper).casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_APPROVED, null);
        }

        @Test
        void agreeRoutesReturnTypesToReturning() {
            when(aftersaleOrderMapper.selectById(ID)).thenReturn(order(AftersaleConsts.TYPE_RETURN_REFUND));
            when(aftersaleOrderMapper.casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_RETURNING, null)).thenReturn(1);
            aftersaleOrderService.agree(ID, null);
            verify(aftersaleOrderMapper).casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_RETURNING, null);

            when(aftersaleOrderMapper.selectById(ID)).thenReturn(order(AftersaleConsts.TYPE_EXCHANGE));
            aftersaleOrderService.agree(ID, null);
            verify(aftersaleOrderMapper, times(2)).casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_RETURNING, null);
        }

        @Test
        void agreeFailsWhenOrderMissingOrNotPending() {
            when(aftersaleOrderMapper.selectById(404L)).thenReturn(null);
            BusinessException missing = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.agree(404L, null));
            assertTrue(missing.getMessage().contains("售后单不存在"));

            when(aftersaleOrderMapper.selectById(ID)).thenReturn(order(AftersaleConsts.TYPE_REFUND_ONLY));
            when(aftersaleOrderMapper.casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_APPROVED, null)).thenReturn(0);
            BusinessException guarded = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.agree(ID, null));
            assertTrue(guarded.getMessage().contains("同意失败"));
        }

        @Test
        void agreeRejectsUnknownType() {
            when(aftersaleOrderMapper.selectById(ID)).thenReturn(order("UNKNOWN"));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.agree(ID, null));
            assertTrue(ex.getMessage().contains("售后类型无法识别"));
            verify(aftersaleOrderMapper, never()).casStatus(any(), any(), any(), any());
        }

        @Test
        void rejectRequiresResult() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.reject(ID, "  "));
            assertTrue(ex.getMessage().contains("拒绝必须填写处理结果"));
            verify(aftersaleOrderMapper, never()).casStatus(any(), any(), any(), any());
        }

        @Test
        void rejectSucceedsAndFillsResult() {
            when(aftersaleOrderMapper.casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_REJECTED, "质量不合格拒退")).thenReturn(1);
            aftersaleOrderService.reject(ID, "质量不合格拒退");
            verify(aftersaleOrderMapper).casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_REJECTED, "质量不合格拒退");
        }

        @Test
        void rejectFailsWhenCasMisses() {
            when(aftersaleOrderMapper.casStatus(ID, AftersaleConsts.STATUS_PENDING,
                    AftersaleConsts.STATUS_REJECTED, "x")).thenReturn(0);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.reject(ID, "x"));
            assertTrue(ex.getMessage().contains("拒绝失败"));
        }

        @Test
        void refundSucceedsViaGuardedUpdateAndFailsWhenGuardMisses() {
            when(aftersaleOrderMapper.refundOrder(ID, null)).thenReturn(1);
            aftersaleOrderService.refund(ID, null);
            verify(aftersaleOrderMapper).refundOrder(ID, null);

            when(aftersaleOrderMapper.refundOrder(ID, null)).thenReturn(0);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.refund(ID, null));
            assertTrue(ex.getMessage().contains("退款失败"));
        }

        @Test
        void completeSucceedsAndFailsWhenNotRefunded() {
            when(aftersaleOrderMapper.casStatus(ID, AftersaleConsts.STATUS_REFUNDED,
                    AftersaleConsts.STATUS_COMPLETED, "退款已到账")).thenReturn(1);
            aftersaleOrderService.complete(ID, "退款已到账");
            verify(aftersaleOrderMapper).casStatus(ID, AftersaleConsts.STATUS_REFUNDED,
                    AftersaleConsts.STATUS_COMPLETED, "退款已到账");

            when(aftersaleOrderMapper.casStatus(ID, AftersaleConsts.STATUS_REFUNDED,
                    AftersaleConsts.STATUS_COMPLETED, null)).thenReturn(0);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.complete(ID, null));
            assertTrue(ex.getMessage().contains("完成失败"));
        }
    }

    /** 收退件复合动作组(#12 退货入库,2026-09-04 拍板):校验链前置 → cas 占位 → IN_RETURN 动账 → 明细落库 */
    @Nested
    class ReturnReceiveInventory {

        private static final Long ORDER_ID = 100L;
        private static final Long WAREHOUSE_ID = 2L;
        private static final Long ORDER_ITEM_ID = 11L;
        private static final Long SKU_ID = 501L;

        private AftersaleOrder returningOrder() {
            return AftersaleOrder.builder().id(ID).aftersaleNo("AS001")
                    .type(AftersaleConsts.TYPE_RETURN_REFUND).status(AftersaleConsts.STATUS_RETURNING)
                    .orderId(ORDER_ID).build();
        }

        private ShopOrderApi.OrderDeliveryView view() {
            return ShopOrderApi.OrderDeliveryView.builder().orderId(ORDER_ID)
                    .items(List.of(ShopOrderApi.OrderDeliveryView.Item.builder()
                            .orderItemId(ORDER_ITEM_ID).skuId(SKU_ID).quantity(3).build()))
                    .build();
        }

        private AftersaleReturnReceiveRequest request(Integer returnQty) {
            return AftersaleReturnReceiveRequest.builder().warehouseId(WAREHOUSE_ID)
                    .items(List.of(AftersaleReturnItemRequest.builder()
                            .orderItemId(ORDER_ITEM_ID).returnQty(returnQty).build()))
                    .result("外包装完好").build();
        }

        private void stubHappyPath() {
            when(aftersaleOrderMapper.selectById(ID)).thenReturn(returningOrder());
            when(warehouseApi.existsWarehouse(WAREHOUSE_ID)).thenReturn(true);
            when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view());
            when(aftersaleReturnItemMapper.listByOrderId(ORDER_ID)).thenReturn(List.of());
            when(aftersaleOrderMapper.receiveReturn(ID, WAREHOUSE_ID, "外包装完好")).thenReturn(1);
        }

        @Test
        void receiveReturnOccupiesThenMovesInventoryAndInsertsItems() {
            stubHappyPath();
            aftersaleOrderService.receiveReturn(ID, request(1));
            verify(aftersaleOrderMapper).receiveReturn(ID, WAREHOUSE_ID, "外包装完好");
            ArgumentCaptor<InventoryChangeCommand> captor = ArgumentCaptor.forClass(InventoryChangeCommand.class);
            verify(inventoryChangeApi).change(captor.capture());
            InventoryChangeCommand command = captor.getValue();
            assertEquals(SKU_ID, command.skuId());
            assertEquals(WAREHOUSE_ID, command.warehouseId());
            assertEquals(1, command.quantity());
            assertEquals(InventoryConsts.FLOW_TYPE_IN_RETURN, command.flowType());
            assertEquals(AftersaleConsts.BIZ_TYPE_AFTERSALE_ORDER, command.bizType());
            assertEquals(ID, command.bizId());
            verify(aftersaleReturnItemMapper).insert(any(AftersaleReturnItem.class));
        }

        @Test
        void receiveReturnRejectsMissingWarehouseOrItems() {
            BusinessException noWarehouse = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, AftersaleReturnReceiveRequest.builder()
                            .items(request(1).items()).build()));
            assertTrue(noWarehouse.getMessage().contains("必须指定退货入库仓"));

            BusinessException noItems = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, AftersaleReturnReceiveRequest.builder()
                            .warehouseId(WAREHOUSE_ID).result(null).build()));
            assertTrue(noItems.getMessage().contains("必须录入实收退货明细"));
            verify(aftersaleOrderMapper, never()).receiveReturn(any(), any(), any());
        }

        @Test
        void receiveReturnRejectsMissingOrderWarehouseAndView() {
            when(aftersaleOrderMapper.selectById(404L)).thenReturn(null);
            BusinessException missing = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(404L, request(1)));
            assertTrue(missing.getMessage().contains("售后单不存在"));

            when(aftersaleOrderMapper.selectById(ID)).thenReturn(returningOrder());
            when(warehouseApi.existsWarehouse(WAREHOUSE_ID)).thenReturn(false);
            BusinessException badWarehouse = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, request(1)));
            assertTrue(badWarehouse.getMessage().contains("退货仓库不存在"));

            when(warehouseApi.existsWarehouse(WAREHOUSE_ID)).thenReturn(true);
            when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(null);
            BusinessException badOrder = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, request(1)));
            assertTrue(badOrder.getMessage().contains("关联订单不存在"));
            verify(aftersaleOrderMapper, never()).receiveReturn(any(), any(), any());
        }

        @Test
        void receiveReturnRejectsForeignOrUnboundOrderItemAndDuplicateLine() {
            stubHappyPath();
            AftersaleReturnItemRequest foreign = AftersaleReturnItemRequest.builder().orderItemId(99L).returnQty(1).build();
            BusinessException badLine = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, AftersaleReturnReceiveRequest.builder()
                            .warehouseId(WAREHOUSE_ID).items(List.of(foreign)).build()));
            assertTrue(badLine.getMessage().contains("不属于该订单或 SKU 未绑定"));

            AftersaleReturnItemRequest line = AftersaleReturnItemRequest.builder()
                    .orderItemId(ORDER_ITEM_ID).returnQty(1).build();
            BusinessException duplicate = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, AftersaleReturnReceiveRequest.builder()
                            .warehouseId(WAREHOUSE_ID).items(List.of(line, line)).build()));
            assertTrue(duplicate.getMessage().contains("实收明细行重复"));

            BusinessException nonPositive = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, request(0)));
            assertTrue(nonPositive.getMessage().contains("退货数量必须为正数"));
            verify(aftersaleOrderMapper, never()).receiveReturn(any(), any(), any());
            verify(inventoryChangeApi, never()).change(any());
        }

        @Test
        void receiveReturnRejectsQtyOverCapCountingHistory() {
            stubHappyPath();
            when(aftersaleReturnItemMapper.listByOrderId(ORDER_ID)).thenReturn(List.of(
                    AftersaleReturnItem.builder().orderItemId(ORDER_ITEM_ID).returnQty(2).build()));
            BusinessException overCap = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, request(2)));
            assertTrue(overCap.getMessage().contains("退货数量超限"));
            verify(inventoryChangeApi, never()).change(any());
            verify(aftersaleReturnItemMapper, never()).insert(any(AftersaleReturnItem.class));
        }

        @Test
        void receiveReturnFailsWhenCasMissesWithoutSideEffects() {
            stubHappyPath();
            when(aftersaleOrderMapper.receiveReturn(ID, WAREHOUSE_ID, "外包装完好")).thenReturn(0);
            BusinessException guarded = assertThrows(BusinessException.class,
                    () -> aftersaleOrderService.receiveReturn(ID, request(1)));
            assertTrue(guarded.getMessage().contains("收退件失败"));
            verify(inventoryChangeApi, never()).change(any());
            verify(aftersaleReturnItemMapper, never()).insert(any(AftersaleReturnItem.class));
        }
    }

    /** 平台售后同步落库组(#12):必填守卫 / 订单未入库跳过 / 状态映射全分支 / upsert 实参核对 */
    @Nested
    class SyncRefund {

        private static final Long SHOP_ID = 100L;

        private static final Long REFUND_ORDER_ID = 200L;

        private UnifiedRefund refund(UnifiedRefund.RefundType type, UnifiedRefund.RefundStatus status) {
            return UnifiedRefund.builder()
                    .platformRefundId("R-1")
                    .platformOrderId("PO-1")
                    .shopId(SHOP_ID)
                    .type(type)
                    .status(status)
                    .currency("USD")
                    .refundAmount(new BigDecimal("12.3400"))
                    .reason("尺寸不符")
                    .build();
        }

        /** 变体构造:UnifiedRefund 为 @Data,测试构造位用 setter 白名单改字段(非读改写生命周期,docs/07 §1) */
        private UnifiedRefund mutated(UnifiedRefund base, java.util.function.Consumer<UnifiedRefund> mutator) {
            mutator.accept(base);
            return base;
        }

        @Test
        void saveRejectsMissingRequiredFields() {
            assertThrows(BusinessException.class, () -> aftersaleOrderService.saveUnifiedRefund(null));
            assertThrows(BusinessException.class, () -> aftersaleOrderService.saveUnifiedRefund(
                    mutated(refund(UnifiedRefund.RefundType.REFUND_ONLY, UnifiedRefund.RefundStatus.APPLYING),
                            r -> r.setShopId(null))));
            assertThrows(BusinessException.class, () -> aftersaleOrderService.saveUnifiedRefund(
                    mutated(refund(UnifiedRefund.RefundType.REFUND_ONLY, UnifiedRefund.RefundStatus.APPLYING),
                            r -> r.setPlatformRefundId(" "))));
            assertThrows(BusinessException.class, () -> aftersaleOrderService.saveUnifiedRefund(
                    mutated(refund(UnifiedRefund.RefundType.REFUND_ONLY, UnifiedRefund.RefundStatus.APPLYING),
                            r -> r.setPlatformOrderId(null))));
            assertThrows(BusinessException.class, () -> aftersaleOrderService.saveUnifiedRefund(
                    refund(null, UnifiedRefund.RefundStatus.APPLYING)));
            assertThrows(BusinessException.class, () -> aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.REFUND_ONLY, null)));
            assertThrows(BusinessException.class, () -> aftersaleOrderService.saveUnifiedRefund(
                    mutated(refund(UnifiedRefund.RefundType.REFUND_ONLY, UnifiedRefund.RefundStatus.APPLYING),
                            r -> r.setCurrency(" "))));
            verify(aftersaleOrderMapper, never()).upsert(any());
            verify(shopOrderApi, never()).findIdByPlatformOrderId(any(), any());
        }

        @Test
        void saveSkipsWhenOrderNotPulledYet() {
            when(shopOrderApi.findIdByPlatformOrderId(SHOP_ID, "PO-1")).thenReturn(null);
            assertFalse(aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.RETURN_REFUND, UnifiedRefund.RefundStatus.APPLYING)));
            verify(aftersaleOrderMapper, never()).upsert(any());
        }

        @Test
        void saveMapsInitialStatusAndUpsertsAllFields() {
            when(shopOrderApi.findIdByPlatformOrderId(SHOP_ID, "PO-1")).thenReturn(REFUND_ORDER_ID);
            assertTrue(aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.RETURN_REFUND, UnifiedRefund.RefundStatus.APPLYING)));
            ArgumentCaptor<AftersaleOrder> captor = ArgumentCaptor.forClass(AftersaleOrder.class);
            verify(aftersaleOrderMapper).upsert(captor.capture());
            AftersaleOrder saved = captor.getValue();
            assertEquals("R-1", saved.getAftersaleNo());
            assertEquals(SHOP_ID, saved.getShopId());
            assertEquals("R-1", saved.getPlatformRefundId());
            assertEquals(REFUND_ORDER_ID, saved.getOrderId());
            assertEquals(AftersaleConsts.TYPE_RETURN_REFUND, saved.getType());
            assertEquals(AftersaleConsts.STATUS_PENDING, saved.getStatus());
            assertEquals(new BigDecimal("12.3400"), saved.getRefundAmount());
            assertEquals("USD", saved.getCurrency());
            assertEquals("尺寸不符", saved.getReason());
        }

        @Test
        void saveRoutesFinishedByType() {
            when(shopOrderApi.findIdByPlatformOrderId(SHOP_ID, "PO-1")).thenReturn(REFUND_ORDER_ID);
            aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.REFUND_ONLY, UnifiedRefund.RefundStatus.FINISHED));
            aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.RETURN_REFUND, UnifiedRefund.RefundStatus.FINISHED));
            aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.EXCHANGE, UnifiedRefund.RefundStatus.FINISHED));
            aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.RESEND, UnifiedRefund.RefundStatus.FINISHED));
            ArgumentCaptor<AftersaleOrder> captor = ArgumentCaptor.forClass(AftersaleOrder.class);
            verify(aftersaleOrderMapper, times(4)).upsert(captor.capture());
            assertEquals(List.of(AftersaleConsts.STATUS_REFUNDED, AftersaleConsts.STATUS_REFUNDED,
                    AftersaleConsts.STATUS_COMPLETED, AftersaleConsts.STATUS_COMPLETED),
                    captor.getAllValues().stream().map(AftersaleOrder::getStatus).toList());
        }

        @Test
        void saveMapsNonFinishedStatuses() {
            when(shopOrderApi.findIdByPlatformOrderId(SHOP_ID, "PO-1")).thenReturn(REFUND_ORDER_ID);
            aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.RETURN_REFUND, UnifiedRefund.RefundStatus.WAIT_RECEIVE));
            aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.RETURN_REFUND, UnifiedRefund.RefundStatus.REJECTED));
            aftersaleOrderService.saveUnifiedRefund(
                    refund(UnifiedRefund.RefundType.RETURN_REFUND, UnifiedRefund.RefundStatus.CANCELLED));
            ArgumentCaptor<AftersaleOrder> captor = ArgumentCaptor.forClass(AftersaleOrder.class);
            verify(aftersaleOrderMapper, times(3)).upsert(captor.capture());
            assertEquals(List.of(AftersaleConsts.STATUS_RETURNING, AftersaleConsts.STATUS_REJECTED,
                    AftersaleConsts.STATUS_CANCELLED),
                    captor.getAllValues().stream().map(AftersaleOrder::getStatus).toList());
        }

        @Test
        void saveFallsBackToDescriptionAndTruncatesReason() {
            when(shopOrderApi.findIdByPlatformOrderId(SHOP_ID, "PO-1")).thenReturn(REFUND_ORDER_ID);
            aftersaleOrderService.saveUnifiedRefund(mutated(
                    refund(UnifiedRefund.RefundType.REFUND_ONLY, UnifiedRefund.RefundStatus.APPLYING),
                    r -> r.setReason(null)));
            aftersaleOrderService.saveUnifiedRefund(mutated(
                    refund(UnifiedRefund.RefundType.REFUND_ONLY, UnifiedRefund.RefundStatus.APPLYING),
                    r -> {
                        r.setReason(null);
                        r.setDescription("七天内无理由");
                    }));
            aftersaleOrderService.saveUnifiedRefund(mutated(
                    refund(UnifiedRefund.RefundType.REFUND_ONLY, UnifiedRefund.RefundStatus.APPLYING),
                    r -> r.setReason("长".repeat(600))));
            ArgumentCaptor<AftersaleOrder> captor = ArgumentCaptor.forClass(AftersaleOrder.class);
            verify(aftersaleOrderMapper, times(3)).upsert(captor.capture());
            assertNull(captor.getAllValues().get(0).getReason());
            assertEquals("七天内无理由", captor.getAllValues().get(1).getReason());
            assertEquals(512, captor.getAllValues().get(2).getReason().length());
        }
    }
}
