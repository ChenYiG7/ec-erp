package com.own.erp.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.WarehouseApi;
import com.own.erp.purchase.constant.PurchaseConsts;
import com.own.erp.purchase.entity.PurchaseOrder;
import com.own.erp.purchase.entity.PurchaseOrderItem;
import com.own.erp.purchase.entity.Supplier;
import com.own.erp.purchase.mapper.PurchaseInboundMapper;
import com.own.erp.purchase.mapper.PurchaseOrderItemMapper;
import com.own.erp.purchase.mapper.PurchaseOrderMapper;
import com.own.erp.purchase.mapper.SupplierMapper;
import com.own.erp.purchase.request.command.PurchaseOrderItemSaveRequest;
import com.own.erp.purchase.request.command.PurchaseOrderSaveRequest;
import com.own.erp.purchase.request.query.PurchaseOrderQuery;
import com.own.erp.purchase.response.PurchaseOrderItemResponse;
import com.own.erp.purchase.response.PurchaseOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : PurchaseOrderService 单测(AIR:mock Mapper 与契约接口,不依赖数据库)。
 *     必测面(docs/07 §10):金额汇总、状态机流转、删除校验、核销回写(#10)
 */
class PurchaseOrderServiceTest {

    private PurchaseOrderMapper purchaseOrderMapper;
    private PurchaseOrderItemMapper purchaseOrderItemMapper;
    private PurchaseInboundMapper purchaseInboundMapper;
    private SupplierMapper supplierMapper;
    private GoodsSkuApi goodsSkuApi;
    private WarehouseApi warehouseApi;
    private PurchaseOrderService purchaseOrderService;

    @BeforeEach
    void setUp() {
        purchaseOrderMapper = mock(PurchaseOrderMapper.class);
        purchaseOrderItemMapper = mock(PurchaseOrderItemMapper.class);
        purchaseInboundMapper = mock(PurchaseInboundMapper.class);
        supplierMapper = mock(SupplierMapper.class);
        goodsSkuApi = mock(GoodsSkuApi.class);
        warehouseApi = mock(WarehouseApi.class);
        purchaseOrderService = new PurchaseOrderService(purchaseOrderMapper, purchaseOrderItemMapper,
                purchaseInboundMapper, supplierMapper, goodsSkuApi, warehouseApi);
        // 默认放行引用校验,各用例按需覆盖
        when(supplierMapper.selectById(any())).thenReturn(new Supplier());
        when(warehouseApi.existsWarehouse(any())).thenReturn(true);
        when(goodsSkuApi.existsSku(any())).thenReturn(true);
    }

    // 注:countItemRefsBySkuIds 内部用 Lambda wrapper .in()(急切解析列元数据),纯 Mockito 环境不可直测;
    // 其正确性由 erp-api GoodsReferenceApiImplTest 汇总链路与运行时覆盖(.eq 系列为懒解析,可测)

    /** 合法请求:两行明细,一行有价 2.5×10=25,一行赠品无价 3×0=0 */
    private PurchaseOrderSaveRequest validRequest() {
        return PurchaseOrderSaveRequest.builder()
                .poNo("PO001")
                .supplierId(1L)
                .warehouseId(2L)
                .items(List.of(
                        PurchaseOrderItemSaveRequest.builder()
                                .skuId(100L).quantity(10).purchasePrice(new BigDecimal("2.5")).build(),
                        PurchaseOrderItemSaveRequest.builder()
                                .skuId(200L).quantity(3).build()))
                .build();
    }

    @Nested
    class Save {

        @Test
        void computesTotalAndForcesDraftThenInsertsItems() {
            purchaseOrderService.save(validRequest());
            ArgumentCaptor<PurchaseOrder> poCaptor = ArgumentCaptor.forClass(PurchaseOrder.class);
            verify(purchaseOrderMapper).insert(poCaptor.capture());
            assertEquals(PurchaseConsts.PO_DRAFT, poCaptor.getValue().getStatus());
            assertEquals(new BigDecimal("25.0"), poCaptor.getValue().getTotalAmount());

            ArgumentCaptor<PurchaseOrderItem> itemCaptor = ArgumentCaptor.forClass(PurchaseOrderItem.class);
            verify(purchaseOrderItemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
            List<PurchaseOrderItem> items = itemCaptor.getAllValues();
            assertEquals(100L, items.get(0).getSkuId());
            assertEquals(0, items.get(0).getArrivedQty());
            assertEquals(new BigDecimal("2.5"), items.get(0).getPurchasePrice());
            // 赠品无价按 0 计落库
            assertEquals(BigDecimal.ZERO, items.get(1).getPurchasePrice());
        }

        @Test
        void rejectsEmptyItems() {
            BusinessException e = assertThrows(BusinessException.class, () -> purchaseOrderService.save(
                    PurchaseOrderSaveRequest.builder().poNo("PO001").supplierId(1L).warehouseId(2L).build()));
            assertTrue(e.getMessage().contains("采购明细不能为空"));
        }

        @Test
        void rejectsDuplicateSkuLines() {
            PurchaseOrderSaveRequest request = PurchaseOrderSaveRequest.builder()
                    .poNo("PO001").supplierId(1L).warehouseId(2L)
                    .items(List.of(
                            PurchaseOrderItemSaveRequest.builder().skuId(100L).quantity(1).build(),
                            PurchaseOrderItemSaveRequest.builder().skuId(100L).quantity(2).build()))
                    .build();
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.save(request))
                    .getMessage().contains("重复"));
        }

        @Test
        void rejectsMissingSupplier() {
            when(supplierMapper.selectById(any())).thenReturn(null);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.save(validRequest()))
                    .getMessage().contains("供应商不存在"));
        }

        @Test
        void rejectsMissingWarehouse() {
            when(warehouseApi.existsWarehouse(any())).thenReturn(false);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.save(validRequest()))
                    .getMessage().contains("仓库不存在"));
        }

        @Test
        void rejectsMissingSku() {
            when(goodsSkuApi.existsSku(200L)).thenReturn(false);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.save(validRequest()))
                    .getMessage().contains("SKU 不存在"));
        }

        @Test
        void translatesDuplicatePoNoToFriendlyError() {
            when(purchaseOrderMapper.insert(any(PurchaseOrder.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("uk_po_no"));
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.save(validRequest()))
                    .getMessage().contains("采购单号已存在"));
        }
    }

    @Nested
    class Update {

        @Test
        void replacesItemsAndRecomputesForDraft() {
            PurchaseOrder draft = new PurchaseOrder();
            draft.setId(9L);
            draft.setStatus(PurchaseConsts.PO_DRAFT);
            when(purchaseOrderMapper.selectById(9L)).thenReturn(draft);

            purchaseOrderService.update(9L, validRequest());

            ArgumentCaptor<PurchaseOrder> captor = ArgumentCaptor.forClass(PurchaseOrder.class);
            verify(purchaseOrderMapper).updateById(captor.capture());
            assertEquals(9L, captor.getValue().getId());
            assertEquals(new BigDecimal("25.0"), captor.getValue().getTotalAmount());
            verify(purchaseOrderItemMapper).delete(any());
            verify(purchaseOrderItemMapper, org.mockito.Mockito.times(2)).insert(any(PurchaseOrderItem.class));
        }

        @Test
        void rejectsNonDraft() {
            PurchaseOrder audited = new PurchaseOrder();
            audited.setStatus(PurchaseConsts.PO_AUDITED);
            when(purchaseOrderMapper.selectById(9L)).thenReturn(audited);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.update(9L, validRequest()))
                    .getMessage().contains("仅草稿状态可修改"));
            verify(purchaseOrderMapper, never()).updateById(any(PurchaseOrder.class));
        }
    }

    @Nested
    class StateMachine {

        @Test
        void auditSucceedsOnCasHitAndFailsOnMiss() {
            when(purchaseOrderMapper.casStatus(1L, PurchaseConsts.PO_DRAFT, PurchaseConsts.PO_AUDITED)).thenReturn(1);
            purchaseOrderService.audit(1L);

            when(purchaseOrderMapper.casStatus(2L, PurchaseConsts.PO_DRAFT, PurchaseConsts.PO_AUDITED)).thenReturn(0);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.audit(2L))
                    .getMessage().contains("审核失败"));
        }

        @Test
        void closeSucceedsOnCasHitAndFailsOnMiss() {
            when(purchaseOrderMapper.closeOrder(1L)).thenReturn(1);
            purchaseOrderService.close(1L);

            when(purchaseOrderMapper.closeOrder(2L)).thenReturn(0);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.close(2L))
                    .getMessage().contains("关闭失败"));
        }
    }

    @Nested
    class Delete {

        @Test
        void removesItemsAndOrderForDraftWithoutInbound() {
            PurchaseOrder draft = new PurchaseOrder();
            draft.setStatus(PurchaseConsts.PO_DRAFT);
            when(purchaseOrderMapper.selectById(1L)).thenReturn(draft);
            when(purchaseInboundMapper.selectCount(any())).thenReturn(0L);

            purchaseOrderService.delete(1L);

            verify(purchaseOrderItemMapper).delete(any());
            verify(purchaseOrderMapper).deleteById(1L);
        }

        @Test
        void rejectsNonDraft() {
            PurchaseOrder received = new PurchaseOrder();
            received.setStatus(PurchaseConsts.PO_RECEIVED);
            when(purchaseOrderMapper.selectById(1L)).thenReturn(received);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.delete(1L))
                    .getMessage().contains("仅草稿状态可删除"));
        }

        @Test
        void rejectsWhenInboundExists() {
            PurchaseOrder draft = new PurchaseOrder();
            draft.setStatus(PurchaseConsts.PO_DRAFT);
            when(purchaseOrderMapper.selectById(1L)).thenReturn(draft);
            when(purchaseInboundMapper.selectCount(any())).thenReturn(2L);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.delete(1L))
                    .getMessage().contains("已有入库记录"));
            verify(purchaseOrderMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    class ReceiveQuantities {

        @Test
        void advancesPartialWhenSomeLinesRemain() {
            when(purchaseOrderItemMapper.increaseArrivedQty(501L, 50)).thenReturn(1);
            when(purchaseOrderItemMapper.selectList(any())).thenReturn(List.of(
                    item(501L, 10, 10),
                    item(502L, 3, 10)));
            when(purchaseOrderMapper.advanceOnReceive(88L, PurchaseConsts.PO_PARTIAL_RECEIVED)).thenReturn(1);

            purchaseOrderService.receiveQuantities(88L, List.of(new PurchaseOrderService.ReceiveLine(501L, 50)));

            verify(purchaseOrderMapper).advanceOnReceive(88L, PurchaseConsts.PO_PARTIAL_RECEIVED);
        }

        @Test
        void advancesReceivedWhenAllLinesComplete() {
            when(purchaseOrderItemMapper.increaseArrivedQty(anyLong(), anyInt())).thenReturn(1);
            when(purchaseOrderItemMapper.selectList(any())).thenReturn(List.of(
                    item(501L, 10, 10),
                    item(502L, 10, 10)));
            when(purchaseOrderMapper.advanceOnReceive(88L, PurchaseConsts.PO_RECEIVED)).thenReturn(1);

            purchaseOrderService.receiveQuantities(88L, List.of(new PurchaseOrderService.ReceiveLine(501L, 50)));
            verify(purchaseOrderMapper).advanceOnReceive(88L, PurchaseConsts.PO_RECEIVED);
        }

        @Test
        void rejectsOverReceiveOrMissingItem() {
            when(purchaseOrderItemMapper.increaseArrivedQty(501L, 50)).thenReturn(0);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.receiveQuantities(88L,
                    List.of(new PurchaseOrderService.ReceiveLine(501L, 50)))).getMessage().contains("超出采购明细剩余量"));
        }

        @Test
        void rejectsWhenOrderClosedConcurrently() {
            when(purchaseOrderItemMapper.increaseArrivedQty(anyLong(), anyInt())).thenReturn(1);
            when(purchaseOrderItemMapper.selectList(any())).thenReturn(List.of(item(501L, 10, 10)));
            when(purchaseOrderMapper.advanceOnReceive(eq(88L), any())).thenReturn(0);
            assertTrue(assertThrows(BusinessException.class, () -> purchaseOrderService.receiveQuantities(88L,
                    List.of(new PurchaseOrderService.ReceiveLine(501L, 50)))).getMessage().contains("状态已变化"));
        }
    }

    private PurchaseOrderItem item(Long id, int arrivedQty, int quantity) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setArrivedQty(arrivedQty);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void getByIdCarriesItemsAndReturnsNullWhenMissing() {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setId(1L);
        when(purchaseOrderMapper.selectById(1L)).thenReturn(purchaseOrder);
        when(purchaseOrderItemMapper.selectList(any())).thenReturn(List.of(item(501L, 0, 5)));

        PurchaseOrderResponse response = purchaseOrderService.getById(1L);
        assertEquals(1, response.items().size());
        assertEquals(PurchaseOrderItemResponse.from(item(501L, 0, 5)), response.items().get(0));
        assertNull(purchaseOrderService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponseWithoutItems() {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setId(2L);
        Page<PurchaseOrder> page = new Page<>(1, 10);
        page.setRecords(List.of(purchaseOrder));
        doReturn(page).when(purchaseOrderMapper).selectPage(any(), any());
        Page<PurchaseOrderResponse> result = purchaseOrderService.page(new PurchaseOrderQuery());
        assertEquals(2L, result.getRecords().get(0).id());
        assertNull(result.getRecords().get(0).items());
    }

    @Test
    void countBySupplierIdReturnsCount() {
        when(purchaseOrderMapper.selectCount(any())).thenReturn(3L);
        assertEquals(3L, purchaseOrderService.countBySupplierId(1L));
    }
}
