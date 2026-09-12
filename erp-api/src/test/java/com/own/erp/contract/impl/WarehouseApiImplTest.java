package com.own.erp.contract.impl;

import com.own.erp.contract.WarehouseApi;
import com.own.erp.inventory.service.InventoryService;
import com.own.erp.inventory.service.StocktakeOrderService;
import com.own.erp.inventory.service.TransferOrderService;
import com.own.erp.purchase.service.PurchaseOrderService;
import com.own.erp.warehouse.service.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : WarehouseApiImpl 单测:存在性契约透传 erp-warehouse WarehouseService(#10);
 *     删除引用计数 = 库存 + 采购 + 盘点 + 调拨四域合计(#7,#10 遗留收口,#30 余量扩容)
 */
class WarehouseApiImplTest {

    private WarehouseService warehouseService;
    private InventoryService inventoryService;
    private PurchaseOrderService purchaseOrderService;
    private StocktakeOrderService stocktakeOrderService;
    private TransferOrderService transferOrderService;
    private WarehouseApi warehouseApi;

    @BeforeEach
    void setUp() {
        warehouseService = mock(WarehouseService.class);
        inventoryService = mock(InventoryService.class);
        purchaseOrderService = mock(PurchaseOrderService.class);
        stocktakeOrderService = mock(StocktakeOrderService.class);
        transferOrderService = mock(TransferOrderService.class);
        warehouseApi = new WarehouseApiImpl(warehouseService, inventoryService, purchaseOrderService,
                stocktakeOrderService, transferOrderService);
    }

    @Test
    void delegatesExistenceCheck() {
        when(warehouseService.existsWarehouse(2L)).thenReturn(true);
        when(warehouseService.existsWarehouse(404L)).thenReturn(false);
        assertTrue(warehouseApi.existsWarehouse(2L));
        assertFalse(warehouseApi.existsWarehouse(404L));
    }

    @Test
    void countWarehouseRefsSumsFourDomains() {
        when(inventoryService.countByWarehouseId(2L)).thenReturn(4L);
        when(purchaseOrderService.countByWarehouseId(2L)).thenReturn(1L);
        when(stocktakeOrderService.countByWarehouseId(2L)).thenReturn(2L);
        when(transferOrderService.countByWarehouseId(2L)).thenReturn(3L);

        assertEquals(10L, warehouseApi.countWarehouseRefs(2L));
    }

    @Test
    void countWarehouseRefsZeroWhenAllDomainsClean() {
        when(inventoryService.countByWarehouseId(3L)).thenReturn(0L);
        when(purchaseOrderService.countByWarehouseId(3L)).thenReturn(0L);
        when(stocktakeOrderService.countByWarehouseId(3L)).thenReturn(0L);
        when(transferOrderService.countByWarehouseId(3L)).thenReturn(0L);

        assertEquals(0L, warehouseApi.countWarehouseRefs(3L));
    }
}
