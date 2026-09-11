package com.own.erp.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.constant.TransferConsts;
import com.own.erp.inventory.entity.TransferOrder;
import com.own.erp.inventory.entity.TransferOrderItem;
import com.own.erp.inventory.mapper.TransferOrderItemMapper;
import com.own.erp.inventory.mapper.TransferOrderMapper;
import com.own.erp.inventory.request.command.TransferOrderItemSaveRequest;
import com.own.erp.inventory.request.command.TransferOrderSaveRequest;
import com.own.erp.inventory.request.query.TransferOrderQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : TransferOrderService 单测(AIR:mock Mapper/InventoryService,不依赖数据库)。
 *     覆盖状态机生成器射程外的业务断言:引用/明细校验、单号冲突翻译、
 *     confirm 复合事务逐行两腿动账装配(biz_type=TRANSFER_ORDER,biz_id=调拨单)、
 *     调出仓可用不足整单失败包装、确认后禁改禁删;单 cas 守卫用例见 TransferOrderStateMachineTest
 */
class TransferOrderServiceTest {

    private TransferOrderMapper transferOrderMapper;
    private TransferOrderItemMapper transferOrderItemMapper;
    private InventoryService inventoryService;
    private WarehouseApi warehouseApi;
    private GoodsSkuApi goodsSkuApi;
    private CurrentUserApi currentUserApi;
    private TransferOrderService transferOrderService;

    @BeforeEach
    void setUp() {
        transferOrderMapper = mock(TransferOrderMapper.class);
        transferOrderItemMapper = mock(TransferOrderItemMapper.class);
        inventoryService = mock(InventoryService.class);
        warehouseApi = mock(WarehouseApi.class);
        goodsSkuApi = mock(GoodsSkuApi.class);
        currentUserApi = mock(CurrentUserApi.class);
        transferOrderService = new TransferOrderService(transferOrderMapper, transferOrderItemMapper,
                inventoryService, warehouseApi, goodsSkuApi, currentUserApi);
    }

    private TransferOrderSaveRequest request() {
        return TransferOrderSaveRequest.builder()
                .transferNo("TR20260911001").fromWarehouseId(2L).toWarehouseId(3L)
                .items(List.of(
                        TransferOrderItemSaveRequest.builder().skuId(11L).quantity(5).build(),
                        TransferOrderItemSaveRequest.builder().skuId(12L).quantity(3).build()))
                .build();
    }

    private TransferOrder order(Long id, String status) {
        return TransferOrder.builder().id(id).transferNo("TR20260911001")
                .fromWarehouseId(2L).toWarehouseId(3L).status(status).createdBy(7L).build();
    }

    private void stubRefsOk() {
        when(warehouseApi.existsWarehouse(2L)).thenReturn(true);
        when(warehouseApi.existsWarehouse(3L)).thenReturn(true);
        when(goodsSkuApi.existsSku(11L)).thenReturn(true);
        when(goodsSkuApi.existsSku(12L)).thenReturn(true);
        when(currentUserApi.currentUserId()).thenReturn(7L);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        when(transferOrderMapper.selectById(1L)).thenReturn(order(1L, TransferConsts.STATUS_DRAFT));
        assertEquals(1L, transferOrderService.getById(1L).id());
        assertNull(transferOrderService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        Page<TransferOrder> page = new Page<>(1, 10);
        page.setRecords(List.of(order(2L, TransferConsts.STATUS_DRAFT)));
        doReturn(page).when(transferOrderMapper).selectPage(any(), any());
        assertEquals(2L, transferOrderService.page(new TransferOrderQuery()).getRecords().get(0).id());
    }

    @Test
    void saveForcesDraftAndInsertsItems() {
        stubRefsOk();
        doAnswer(inv -> {
            ((TransferOrder) inv.getArgument(0)).setId(88L);
            return 1;
        }).when(transferOrderMapper).insert(any(TransferOrder.class));

        assertEquals(88L, transferOrderService.save(request()));

        ArgumentCaptor<TransferOrder> orderCaptor = ArgumentCaptor.forClass(TransferOrder.class);
        verify(transferOrderMapper).insert(orderCaptor.capture());
        assertEquals(TransferConsts.STATUS_DRAFT, orderCaptor.getValue().getStatus());
        assertEquals(7L, orderCaptor.getValue().getCreatedBy());
        ArgumentCaptor<TransferOrderItem> itemCaptor = ArgumentCaptor.forClass(TransferOrderItem.class);
        verify(transferOrderItemMapper, times(2)).insert(itemCaptor.capture());
        assertEquals(88L, itemCaptor.getAllValues().get(0).getTransferId());
        assertEquals(5, itemCaptor.getAllValues().get(0).getQuantity());
    }

    @Test
    void saveRejectsBadRefsAndItemShapes() {
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.save(
                        TransferOrderSaveRequest.builder().transferNo("TR1").fromWarehouseId(2L).toWarehouseId(2L)
                                .items(List.of(TransferOrderItemSaveRequest.builder().skuId(11L).quantity(1).build())).build()))
                .getMessage().contains("不能相同"));
        stubRefsOk();
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.save(
                        TransferOrderSaveRequest.builder().transferNo("TR2").fromWarehouseId(2L).toWarehouseId(3L).build()))
                .getMessage().contains("明细不能为空"));
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.save(
                        TransferOrderSaveRequest.builder().transferNo("TR3").fromWarehouseId(2L).toWarehouseId(3L)
                                .items(List.of(TransferOrderItemSaveRequest.builder().skuId(11L).quantity(0).build())).build()))
                .getMessage().contains("必须大于0"));
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.save(
                        TransferOrderSaveRequest.builder().transferNo("TR4").fromWarehouseId(2L).toWarehouseId(3L)
                                .items(List.of(TransferOrderItemSaveRequest.builder().skuId(11L).quantity(1).build(),
                                        TransferOrderItemSaveRequest.builder().skuId(11L).quantity(2).build())).build()))
                .getMessage().contains("重复"));
        verify(transferOrderMapper, never()).insert(any(TransferOrder.class));
    }

    @Test
    void saveTranslatesDuplicateNoConflict() {
        stubRefsOk();
        when(transferOrderMapper.insert(any(TransferOrder.class))).thenThrow(new DuplicateKeyException("dup"));
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.save(request()))
                .getMessage().contains("调拨单号已存在"));
    }

    @Test
    void updateRejectsNonDraftOrder() {
        when(transferOrderMapper.selectByIdForUpdate(1L)).thenReturn(order(1L, TransferConsts.STATUS_CONFIRMED));
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.update(1L, request()))
                .getMessage().contains("仅草稿状态可修改"));
        verify(transferOrderMapper, never()).updateById(any(TransferOrder.class));
    }

    @Test
    void confirmCallsTransferPrimitivePerLine() {
        when(transferOrderMapper.casStatus(1L, TransferConsts.STATUS_DRAFT, TransferConsts.STATUS_CONFIRMED)).thenReturn(1);
        when(transferOrderMapper.selectById(1L)).thenReturn(order(1L, TransferConsts.STATUS_DRAFT));
        when(transferOrderItemMapper.selectList(any())).thenReturn(List.of(
                TransferOrderItem.builder().id(100L).transferId(1L).skuId(11L).quantity(5).build(),
                TransferOrderItem.builder().id(101L).transferId(1L).skuId(12L).quantity(3).build()));

        transferOrderService.confirm(1L);

        verify(inventoryService).transfer(11L, 2L, 3L, 5, "调拨单:TR20260911001", 7L, 1L);
        verify(inventoryService).transfer(12L, 2L, 3L, 3, "调拨单:TR20260911001", 7L, 1L);
    }

    @Test
    void confirmRejectsEmptyItemsAndDuplicateConfirmation() {
        when(transferOrderMapper.casStatus(1L, TransferConsts.STATUS_DRAFT, TransferConsts.STATUS_CONFIRMED)).thenReturn(1);
        when(transferOrderMapper.selectById(1L)).thenReturn(order(1L, TransferConsts.STATUS_DRAFT));
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.confirm(1L))
                .getMessage().contains("无明细"));
        verify(inventoryService, never()).transfer(any(), any(), any(), anyInt(), any(), any(), any());

        when(transferOrderMapper.casStatus(1L, TransferConsts.STATUS_DRAFT, TransferConsts.STATUS_CONFIRMED)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.confirm(1L))
                .getMessage().contains("确认失败"));
    }

    @Test
    void confirmWrapsInsufficientAvailableGuard() {
        when(transferOrderMapper.casStatus(1L, TransferConsts.STATUS_DRAFT, TransferConsts.STATUS_CONFIRMED)).thenReturn(1);
        when(transferOrderMapper.selectById(1L)).thenReturn(order(1L, TransferConsts.STATUS_DRAFT));
        when(transferOrderItemMapper.selectList(any())).thenReturn(List.of(
                TransferOrderItem.builder().id(100L).transferId(1L).skuId(11L).quantity(5).build()));
        doThrow(new BusinessException("可用库存不足:当前0,变动-5"))
                .when(inventoryService).transfer(any(), any(), any(), anyInt(), any(), any(), any());

        BusinessException ex = assertThrows(BusinessException.class, () -> transferOrderService.confirm(1L));
        assertTrue(ex.getMessage().contains("调拨失败"));
        assertTrue(ex.getMessage().contains("SKU=11"));
    }

    @Test
    void cancelAndDeleteRejectConfirmedOrder() {
        when(transferOrderMapper.selectById(1L)).thenReturn(order(1L, TransferConsts.STATUS_CONFIRMED));
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.cancel(1L))
                .getMessage().contains("取消失败"));
        assertTrue(assertThrows(BusinessException.class, () -> transferOrderService.delete(1L))
                .getMessage().contains("禁止删除"));
    }
}
