package com.own.erp.ai.graph;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.QueryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : ReplenishCollectNode 单测(#6,AIR:mock 契约接口/建议服务,不依赖数据库):
 *     低库存筛选、跨仓合并求和、空页终止、scanned 计数、待确认建议去重
 */
class ReplenishCollectNodeTest {

    private InventoryQueryApi inventoryQueryApi;
    private ErpAiProperties props;
    private AiSuggestionService aiSuggestionService;
    private ReplenishCollectNode node;

    @BeforeEach
    void setUp() {
        inventoryQueryApi = mock(InventoryQueryApi.class);
        props = new ErpAiProperties();
        props.getReplenish().setScanPageSize(2);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.findPendingSkuIds(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Set.of());
        node = new ReplenishCollectNode(inventoryQueryApi, props, aiSuggestionService);
    }

    private InventoryQueryApi.InventoryView row(Long skuId, Long warehouseId, Integer available, Integer transit) {
        return InventoryQueryApi.InventoryView.builder()
                .skuId(skuId).warehouseId(warehouseId).qtyAvailable(available).qtyTransit(transit).build();
    }

    private InventoryQueryApi.InventoryFilter filter(int pageNo) {
        return InventoryQueryApi.InventoryFilter.builder()
                .pageNo(pageNo).pageSize(props.getReplenish().getScanPageSize()).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersLowStockAndMergesAcrossWarehouses() throws Exception {
        when(inventoryQueryApi.pageInventory(filter(1))).thenReturn(QueryPage.of(List.of(
                row(1L, 1L, 3, 0), row(2L, 1L, 50, 0)), 2));
        when(inventoryQueryApi.pageInventory(filter(2))).thenReturn(QueryPage.of(List.of(
                row(1L, 2L, 2, 5)), 1));

        Map<String, Object> result = node.apply(null);

        assertEquals(3, result.get(ReplenishStateKeys.KEY_SCANNED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        ReplenishItem sku1 = items.get(0);
        assertEquals(1L, sku1.skuId());
        assertEquals(5, sku1.qtyAvailable());
        assertEquals(5, sku1.qtyTransit());
        // 不足页(1 < 2)= 末页,不再翻页
        verify(inventoryQueryApi, times(2)).pageInventory(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyFirstPageYieldsNoItems() throws Exception {
        when(inventoryQueryApi.pageInventory(filter(1))).thenReturn(QueryPage.of(List.of(), 0));

        Map<String, Object> result = node.apply(null);

        assertEquals(0, result.get(ReplenishStateKeys.KEY_SCANNED));
        assertTrue(((List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS)).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingSuggestionDeduped() throws Exception {
        // 去重(2026-09-07 拍板):同 SKU 已存在待确认建议即跳过;扫描计数不受影响
        when(inventoryQueryApi.pageInventory(filter(1))).thenReturn(QueryPage.of(List.of(
                row(1L, 1L, 3, 0), row(2L, 1L, 3, 0)), 2));
        when(aiSuggestionService.findPendingSkuIds("REPLENISH")).thenReturn(Set.of(1L));

        Map<String, Object> result = node.apply(null);

        assertEquals(2, result.get(ReplenishStateKeys.KEY_SCANNED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).skuId());
    }
}
