package com.own.erp.ai.graph;

import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.PurchaseQueryApi;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : PurchaseAggregateNode 单测(#17,AIR:mock 契约/建议服务,不依赖数据库):
 *     供应商分组聚合(总件数/预估金额 BigDecimal 精确累加/缺货标记)、无采购历史 SKU 剔除计数、
 *     待确认建议去重(同供应商整组跳过)、空输入不查库
 */
class PurchaseAggregateNodeTest {

    private PurchaseQueryApi purchaseQueryApi;
    private AiSuggestionService aiSuggestionService;
    private PurchaseAggregateNode node;

    @BeforeEach
    void setUp() {
        purchaseQueryApi = mock(PurchaseQueryApi.class);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.findPendingRefIds(any(), any())).thenReturn(Set.of());
        node = new PurchaseAggregateNode(purchaseQueryApi, aiSuggestionService);
    }

    private ReplenishItem item(Long skuId, int available, int suggestQty) {
        return ReplenishItem.builder().skuId(skuId).qtyAvailable(available).qtyTransit(0)
                .suggestQty(suggestQty).summary("").build();
    }

    private PurchaseQueryApi.SkuSupplierView view(Long skuId, Long supplierId, String supplierName,
                                                  String lastPrice) {
        return PurchaseQueryApi.SkuSupplierView.builder()
                .skuId(skuId).supplierId(supplierId).supplierName(supplierName)
                .lastPrice(lastPrice == null ? null : new BigDecimal(lastPrice))
                .lastPoNo("PO-1").lastPoAt(LocalDateTime.of(2026, 9, 1, 10, 0)).build();
    }

    private OverAllState stateOf(ReplenishItem... items) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(PurchaseStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.input(Map.of(PurchaseStateKeys.KEY_ITEMS, List.of(items)));
        return state;
    }

    @Test
    @SuppressWarnings("unchecked")
    void groupsBySupplierWithExactAmountsAndStockoutFlag() throws Exception {
        // 同供应商两 SKU 聚合:总价 = Σ(单价×建议量);缺货 SKU(可用≤0)→ hasStockout
        when(purchaseQueryApi.findLatestSupplierBySkuIds(any())).thenReturn(List.of(
                view(1L, 10L, "供应商甲", "12.50"),
                view(2L, 10L, "供应商甲", "8.00")));

        Map<String, Object> result = node.apply(stateOf(
                item(1L, 0, 28), item(2L, 5, 18)));

        assertEquals(0, result.get(PurchaseStateKeys.KEY_NO_SUPPLIER));
        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertEquals(1, groups.size());
        PurchaseGroup group = groups.get(0);
        assertEquals(10L, group.supplierId());
        assertEquals("供应商甲", group.supplierName());
        assertEquals(46, group.totalQty());
        // 12.50×28 + 8.00×18 = 350 + 144 = 494.00
        assertEquals(0, new BigDecimal("494.00").compareTo(group.estAmount()));
        assertTrue(group.hasStockout());
        assertEquals(2, group.lines().size());
    }

    @Test
    void nullPriceLineCountsAsZeroEstAmount() throws Exception {
        // 历史行为无价单:单价 null,预估按 0(禁猜价),建议行仍产出
        when(purchaseQueryApi.findLatestSupplierBySkuIds(any())).thenReturn(List.of(
                view(1L, 10L, "供应商甲", null)));

        Map<String, Object> result = node.apply(stateOf(item(1L, 3, 10)));

        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertEquals(0, new BigDecimal("0").compareTo(groups.get(0).estAmount()));
        assertTrue(!groups.get(0).hasStockout());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unmappedSkusCountedAndExcluded() throws Exception {
        // 无采购历史的 SKU 不纳入建议(无法定位供应商),计数上报
        when(purchaseQueryApi.findLatestSupplierBySkuIds(any())).thenReturn(List.of(
                view(1L, 10L, "供应商甲", "12.50")));

        Map<String, Object> result = node.apply(stateOf(item(1L, 0, 28), item(2L, 0, 20)));

        assertEquals(1, result.get(PurchaseStateKeys.KEY_NO_SUPPLIER));
        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).lines().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingSupplierSuggestionDeduped() throws Exception {
        // 去重(同补货 2026-09-07 拍板语义):同供应商存在待确认建议整组跳过
        when(purchaseQueryApi.findLatestSupplierBySkuIds(any())).thenReturn(List.of(
                view(1L, 10L, "供应商甲", "12.50"),
                view(2L, 20L, "供应商乙", "5.00")));
        when(aiSuggestionService.findPendingRefIds("PURCHASE", "SUPPLIER")).thenReturn(Set.of(10L));

        Map<String, Object> result = node.apply(stateOf(item(1L, 0, 28), item(2L, 0, 20)));

        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertEquals(1, groups.size());
        assertEquals(20L, groups.get(0).supplierId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyItemsSkipsContractAndDedupQueries() throws Exception {
        Map<String, Object> result = node.apply(stateOf());

        assertTrue(((List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS)).isEmpty());
        verifyNoInteractions(purchaseQueryApi);
        verifyNoInteractions(aiSuggestionService);
    }
}
