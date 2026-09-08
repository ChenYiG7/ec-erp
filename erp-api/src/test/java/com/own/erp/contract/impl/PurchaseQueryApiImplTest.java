package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.purchase.request.query.PurchaseOrderQuery;
import com.own.erp.purchase.response.PurchaseOrderItemResponse;
import com.own.erp.purchase.response.PurchaseOrderResponse;
import com.own.erp.purchase.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : PurchaseQueryApiImpl 单测(#6 tools 扩容,AIR:mock PurchaseOrderService,不依赖数据库):
 *     过滤/分页参数映射(含默认归一)、行视图/明细显式映射、采购单不存在透传 null
 */
class PurchaseQueryApiImplTest {

    private PurchaseOrderService purchaseOrderService;
    private PurchaseQueryApi purchaseQueryApi;

    @BeforeEach
    void setUp() {
        purchaseOrderService = mock(PurchaseOrderService.class);
        purchaseQueryApi = new PurchaseQueryApiImpl(purchaseOrderService);
    }

    private PurchaseOrderResponse po() {
        return PurchaseOrderResponse.builder()
                .id(1L)
                .poNo("PO-20260908-001")
                .supplierId(2L)
                .warehouseId(3L)
                .status("PARTIAL_RECEIVED")
                .totalAmount(new BigDecimal("1500.0000"))
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build()
                .withItems(List.of(PurchaseOrderItemResponse.builder()
                        .id(11L)
                        .skuId(4L)
                        .quantity(100)
                        .arrivedQty(60)
                        .purchasePrice(new BigDecimal("15.0000"))
                        .build()));
    }

    @Test
    void pagePurchaseOrdersMapsFilterAndPagingIntoDomainQuery() {
        when(purchaseOrderService.page(any())).thenReturn(new Page<>(1, 20, 0));

        purchaseQueryApi.pagePurchaseOrders(PurchaseQueryApi.PurchaseOrderFilter.builder()
                .supplierId(2L).warehouseId(3L).status("AUDITED").pageNo(2).pageSize(50).build());

        ArgumentCaptor<PurchaseOrderQuery> captor = ArgumentCaptor.forClass(PurchaseOrderQuery.class);
        verify(purchaseOrderService).page(captor.capture());
        PurchaseOrderQuery query = captor.getValue();
        assertEquals(2L, query.getSupplierId());
        assertEquals(3L, query.getWarehouseId());
        assertEquals("AUDITED", query.getStatus());
        assertEquals(2, query.getPageNo());
        assertEquals(50, query.getPageSize());
    }

    @Test
    void pagePurchaseOrdersNormalizesMissingPaging() {
        when(purchaseOrderService.page(any())).thenReturn(new Page<>(1, 20, 0));

        purchaseQueryApi.pagePurchaseOrders(PurchaseQueryApi.PurchaseOrderFilter.builder().build());

        ArgumentCaptor<PurchaseOrderQuery> captor = ArgumentCaptor.forClass(PurchaseOrderQuery.class);
        verify(purchaseOrderService).page(captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void pagePurchaseOrdersMapsRowsAndTotal() {
        Page<PurchaseOrderResponse> page = new Page<>(1, 20, 7);
        page.setRecords(List.of(po()));
        when(purchaseOrderService.page(any())).thenReturn(page);

        QueryPage<PurchaseQueryApi.PurchaseOrderView> result = purchaseQueryApi.pagePurchaseOrders(
                PurchaseQueryApi.PurchaseOrderFilter.builder().build());

        assertEquals(7, result.total());
        PurchaseQueryApi.PurchaseOrderView view = result.list().get(0);
        assertEquals(1L, view.id());
        assertEquals("PO-20260908-001", view.poNo());
        assertEquals(2L, view.supplierId());
        assertEquals(3L, view.warehouseId());
        assertEquals("PARTIAL_RECEIVED", view.status());
        assertEquals(0, new BigDecimal("1500.0000").compareTo(view.totalAmount()));
    }

    @Test
    void getPurchaseOrderDetailMapsItems() {
        when(purchaseOrderService.getById(1L)).thenReturn(po());

        PurchaseQueryApi.PurchaseOrderDetail detail = purchaseQueryApi.getPurchaseOrderDetail(1L);

        assertEquals(1L, detail.order().id());
        assertEquals(1, detail.items().size());
        PurchaseQueryApi.PurchaseOrderDetail.Item item = detail.items().get(0);
        assertEquals(11L, item.poItemId());
        assertEquals(4L, item.skuId());
        assertEquals(100, item.quantity());
        assertEquals(60, item.arrivedQty());
        assertEquals(0, new BigDecimal("15.0000").compareTo(item.purchasePrice()));
    }

    @Test
    void getPurchaseOrderDetailReturnsNullWhenMissing() {
        when(purchaseOrderService.getById(99L)).thenReturn(null);
        assertNull(purchaseQueryApi.getPurchaseOrderDetail(99L));
    }

    @Test
    void getPurchaseOrderDetailToleratesNullItems() {
        when(purchaseOrderService.getById(1L)).thenReturn(po().withItems(null));

        assertTrue(purchaseQueryApi.getPurchaseOrderDetail(1L).items().isEmpty());
    }

    @Test
    void findLatestSupplierBySkuIdsMapsProjectionRows() {
        // #17 采购建议取数:域投影行 → 契约视图逐字段映射
        com.own.erp.purchase.response.SkuSupplierRow row = new com.own.erp.purchase.response.SkuSupplierRow();
        row.setSkuId(4L);
        row.setSupplierId(2L);
        row.setSupplierName("供应商甲");
        row.setLastPrice(new BigDecimal("15.0000"));
        row.setLastPoNo("PO-20260908-001");
        row.setLastPoAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(purchaseOrderService.findLatestSupplierBySkuIds(List.of(4L))).thenReturn(List.of(row));

        List<PurchaseQueryApi.SkuSupplierView> views = purchaseQueryApi.findLatestSupplierBySkuIds(List.of(4L));

        assertEquals(1, views.size());
        PurchaseQueryApi.SkuSupplierView view = views.get(0);
        assertEquals(4L, view.skuId());
        assertEquals(2L, view.supplierId());
        assertEquals("供应商甲", view.supplierName());
        assertEquals(0, new BigDecimal("15.0000").compareTo(view.lastPrice()));
        assertEquals("PO-20260908-001", view.lastPoNo());
    }

    @Test
    void findLatestSupplierBySkuIdsPassesEmptyCollectionThrough() {
        // 空集合短路由域 Service 自己收口(契约侧透传断言)
        when(purchaseOrderService.findLatestSupplierBySkuIds(List.of())).thenReturn(List.of());

        assertTrue(purchaseQueryApi.findLatestSupplierBySkuIds(List.of()).isEmpty());
        verify(purchaseOrderService).findLatestSupplierBySkuIds(List.of());
    }
}
