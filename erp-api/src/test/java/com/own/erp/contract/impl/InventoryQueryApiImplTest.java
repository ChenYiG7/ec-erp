package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.inventory.request.query.InventoryQuery;
import com.own.erp.inventory.response.InventoryResponse;
import com.own.erp.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : InventoryQueryApiImpl 单测(#6,AIR:mock InventoryService,不依赖数据库):
 *     skuId/warehouseId 过滤与分页参数映射(含默认归一)、行视图显式映射
 */
class InventoryQueryApiImplTest {

    private InventoryService inventoryService;
    private InventoryQueryApi inventoryQueryApi;

    @BeforeEach
    void setUp() {
        inventoryService = mock(InventoryService.class);
        inventoryQueryApi = new InventoryQueryApiImpl(inventoryService);
    }

    private InventoryResponse row() {
        return InventoryResponse.builder()
                .id(1L)
                .skuId(7L)
                .warehouseId(3L)
                .qtyOnHand(100)
                .qtyLocked(10)
                .qtyTransit(20)
                .qtyAvailable(90)
                .build();
    }

    @Test
    void pageInventoryMapsFilterAndPagingIntoDomainQuery() {
        when(inventoryService.page(any())).thenReturn(new Page<>(1, 20, 0));

        inventoryQueryApi.pageInventory(InventoryQueryApi.InventoryFilter.builder()
                .skuId(7L).warehouseId(3L).pageNo(2).pageSize(10).build());

        ArgumentCaptor<InventoryQuery> captor = ArgumentCaptor.forClass(InventoryQuery.class);
        verify(inventoryService).page(captor.capture());
        InventoryQuery query = captor.getValue();
        assertEquals(7L, query.getSkuId());
        assertEquals(3L, query.getWarehouseId());
        assertEquals(2, query.getPageNo());
        assertEquals(10, query.getPageSize());
    }

    @Test
    void pageInventoryNormalizesMissingPaging() {
        when(inventoryService.page(any())).thenReturn(new Page<>(1, 20, 0));

        inventoryQueryApi.pageInventory(InventoryQueryApi.InventoryFilter.builder().build());

        ArgumentCaptor<InventoryQuery> captor = ArgumentCaptor.forClass(InventoryQuery.class);
        verify(inventoryService).page(captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void pageInventoryMapsRowsAndTotal() {
        Page<InventoryResponse> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(row()));
        when(inventoryService.page(any())).thenReturn(page);

        QueryPage<InventoryQueryApi.InventoryView> result = inventoryQueryApi.pageInventory(
                InventoryQueryApi.InventoryFilter.builder().build());

        assertEquals(1, result.total());
        InventoryQueryApi.InventoryView view = result.list().get(0);
        assertEquals(7L, view.skuId());
        assertEquals(3L, view.warehouseId());
        assertEquals(100, view.qtyOnHand());
        assertEquals(10, view.qtyLocked());
        assertEquals(20, view.qtyTransit());
        assertEquals(90, view.qtyAvailable());
    }
}
