package com.own.erp.ai.graph;

import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.QueryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : LowStockScanner 单测(#6/#17 共享组件,AIR:mock 契约接口,不依赖数据库):
 *     低库存筛选、跨仓合并求和、空页终止、scanned 计数、scanMaxRows 护栏
 *     (2026-09-08 自 ReplenishCollectNodeTest 迁移)
 */
class LowStockScannerTest {

    private InventoryQueryApi inventoryQueryApi;
    private LowStockScanner scanner;

    @BeforeEach
    void setUp() {
        inventoryQueryApi = mock(InventoryQueryApi.class);
        scanner = new LowStockScanner(inventoryQueryApi);
    }

    private InventoryQueryApi.InventoryView row(Long skuId, Long warehouseId, Integer available, Integer transit) {
        return InventoryQueryApi.InventoryView.builder()
                .skuId(skuId).warehouseId(warehouseId).qtyAvailable(available).qtyTransit(transit).build();
    }

    private InventoryQueryApi.InventoryFilter filter(int pageNo, int pageSize) {
        return InventoryQueryApi.InventoryFilter.builder()
                .pageNo(pageNo).pageSize(pageSize).build();
    }

    @Test
    void filtersLowStockAndMergesAcrossWarehouses() {
        when(inventoryQueryApi.pageInventory(filter(1, 2))).thenReturn(QueryPage.of(List.of(
                row(1L, 1L, 3, 0), row(2L, 1L, 50, 0)), 2));
        when(inventoryQueryApi.pageInventory(filter(2, 2))).thenReturn(QueryPage.of(List.of(
                row(1L, 2L, 2, 5)), 1));

        LowStockScanner.ScanResult result = scanner.scan(10, 2, 500);

        assertEquals(3, result.scanned());
        assertEquals(1, result.items().size());
        ReplenishItem sku1 = result.items().get(0);
        assertEquals(1L, sku1.skuId());
        assertEquals(5, sku1.qtyAvailable());
        assertEquals(5, sku1.qtyTransit());
        assertEquals(0, sku1.suggestQty());
        // 不足页(1 < 2)= 末页,不再翻页
        verify(inventoryQueryApi, times(2)).pageInventory(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emptyFirstPageYieldsNoItems() {
        when(inventoryQueryApi.pageInventory(filter(1, 2))).thenReturn(QueryPage.of(List.of(), 0));

        LowStockScanner.ScanResult result = scanner.scan(10, 2, 500);

        assertEquals(0, result.scanned());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void scanMaxRowsGuardrailStopsPaging() {
        // 护栏:单轮总行数达到上限即停(即使仍有下一页),防大表拖死
        when(inventoryQueryApi.pageInventory(filter(1, 2))).thenReturn(QueryPage.of(List.of(
                row(1L, 1L, 1, 0), row(2L, 1L, 1, 0)), 2));
        when(inventoryQueryApi.pageInventory(filter(2, 2))).thenReturn(QueryPage.of(List.of(
                row(3L, 1L, 1, 0)), 1));

        LowStockScanner.ScanResult result = scanner.scan(10, 2, 2);

        assertEquals(2, result.scanned());
        assertEquals(2, result.items().size());
        verify(inventoryQueryApi, times(1)).pageInventory(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nullQtyFieldsSkippedDefensively() {
        // 缺 skuId/缺可用量的行防御跳过(null 不得当 0 误判低库存,禁 NPE 断整轮)
        when(inventoryQueryApi.pageInventory(filter(1, 5))).thenReturn(QueryPage.of(List.of(
                row(null, 1L, 3, 0),
                InventoryQueryApi.InventoryView.builder().skuId(9L).warehouseId(1L).build()), 2));

        LowStockScanner.ScanResult result = scanner.scan(10, 5, 500);

        assertEquals(2, result.scanned());
        assertTrue(result.items().isEmpty());
    }
}
