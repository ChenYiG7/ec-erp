package com.own.erp.ai.graph;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.ProfitQueryApi;
import com.own.erp.contract.ProfitSkuRankRow;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.SalesQueryApi;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SelectionCollectNode 单测(#17 智能选品,AIR:mock 四契约+建议服务,固定时钟):
 *     候选域交集(启用商品 ∩ 有库存行)/死 SKU 剔除/跨仓合并/毛利缺口标记与回填/
 *     销量批量失败降级/库存扫描失败中止/待确认去重
 */
class SelectionCollectNodeTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-08T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private InventoryQueryApi inventoryQueryApi;
    private GoodsQueryApi goodsQueryApi;
    private SalesQueryApi salesQueryApi;
    private ProfitQueryApi profitQueryApi;
    private AiSuggestionService aiSuggestionService;
    private ErpAiProperties props;
    private SelectionCollectNode node;

    @BeforeEach
    void setUp() {
        inventoryQueryApi = mock(InventoryQueryApi.class);
        goodsQueryApi = mock(GoodsQueryApi.class);
        salesQueryApi = mock(SalesQueryApi.class);
        profitQueryApi = mock(ProfitQueryApi.class);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.findPendingRefIds(any(), any())).thenReturn(Set.of());
        props = new ErpAiProperties();
        props.getSelection().setScanPageSize(2);
        node = new SelectionCollectNode(inventoryQueryApi, goodsQueryApi,
                salesQueryApi, profitQueryApi, props, aiSuggestionService, CLOCK);
    }

    private OverAllState state() {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(SelectionStateKeys.KEY_CANDIDATES, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(SelectionStateKeys.KEY_SCANNED, KeyStrategy.REPLACE);
        state.input(Map.of());
        return state;
    }

    private InventoryQueryApi.InventoryView stock(Long skuId, Integer available, Integer transit) {
        return InventoryQueryApi.InventoryView.builder()
                .skuId(skuId).warehouseId(1L).qtyAvailable(available).qtyTransit(transit).build();
    }

    private void stubGoods(Long productId, String name, Long... skuIds) {
        GoodsQueryApi.ProductView product = GoodsQueryApi.ProductView.builder()
                .id(productId).spuCode("SPU" + productId).name(name).status(1).build();
        when(goodsQueryApi.pageProducts(any(GoodsQueryApi.ProductFilter.class)))
                .thenReturn(QueryPage.of(List.of(product), 1));
        List<GoodsQueryApi.SkuView> skus = new ArrayList<>();
        for (Long skuId : skuIds) {
            skus.add(GoodsQueryApi.SkuView.builder()
                    .id(skuId).productId(productId).skuCode("C" + skuId).status(1).build());
        }
        when(goodsQueryApi.listSkusByProductId(productId)).thenReturn(skus);
    }

    /** 30 天逐日序列:近 7 天(09-02~09-08)每天 qty/recentFactor,前 7 天(08-26~09-01)每天 qty/priorFactor */
    private Map<Long, Map<LocalDate, Integer>> series(Long skuId, int recentPerDay, int priorPerDay) {
        Map<LocalDate, Integer> daily = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            daily.put(LocalDate.of(2026, 9, 8).minusDays(i), recentPerDay);
        }
        for (int i = 7; i < 14; i++) {
            daily.put(LocalDate.of(2026, 9, 8).minusDays(i), priorPerDay);
        }
        return Map.of(skuId, daily);
    }

    @Test
    @SuppressWarnings("unchecked")
    void assemblesCandidatesWithIntersectionMergeAndProfit() throws Exception {
        // 库存:sku1 跨仓 5+3=8(在途 2)/sku2 零可用零动销(死)/sku3 可用 3 但商品未启用(剔除)
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenReturn(QueryPage.of(List.of(
                        stock(1L, 5, 2), stock(1L, 3, 0), stock(2L, 0, 0)), 3))
                .thenReturn(QueryPage.of(List.of(stock(3L, 3, 0)), 1))
                .thenReturn(QueryPage.of(List.of(), 0));
        // 两个启用商品一页返回(单次打桩防覆盖):商品甲含 sku1,商品乙含 sku2
        GoodsQueryApi.ProductView productA = GoodsQueryApi.ProductView.builder()
                .id(100L).spuCode("SPU100").name("商品甲").status(1).build();
        GoodsQueryApi.ProductView productB = GoodsQueryApi.ProductView.builder()
                .id(101L).spuCode("SPU101").name("商品乙").status(1).build();
        when(goodsQueryApi.pageProducts(any(GoodsQueryApi.ProductFilter.class)))
                .thenReturn(QueryPage.of(List.of(productA, productB), 2));
        when(goodsQueryApi.listSkusByProductId(100L)).thenReturn(List.of(
                GoodsQueryApi.SkuView.builder().id(1L).productId(100L).skuCode("C1").status(1).build()));
        when(goodsQueryApi.listSkusByProductId(101L)).thenReturn(List.of(
                GoodsQueryApi.SkuView.builder().id(2L).productId(101L).skuCode("C2").status(1).build()));
        when(salesQueryApi.sumQtyBySku(anyCollection(), anyInt()))
                .thenReturn(Map.of(1L, 30, 2L, 0, 3L, 0));
        when(salesQueryApi.listDailyQtyBySku(anyCollection(), anyInt()))
                .thenReturn(series(1L, 2, 2));
        when(profitQueryApi.listSkuProfitRank(any(), anyInt()))
                .thenReturn(List.of(new ProfitSkuRankRow(1L, "商品甲", 5, 30,
                        new BigDecimal("1000"), new BigDecimal("400"),
                        new BigDecimal("-150"), new BigDecimal("300"))));

        Map<String, Object> result = node.apply(state());
        assertEquals(4, result.get(SelectionStateKeys.KEY_SCANNED));
        List<SelectionCandidate> candidates =
                (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_CANDIDATES);
        // sku2 死 SKU 剔除(零动销+零可用);sku3 商品未启用剔除;仅 sku1 入候选
        assertEquals(1, candidates.size());
        SelectionCandidate sku1 = candidates.get(0);
        assertEquals(1L, sku1.skuId());
        assertEquals(8, sku1.qtyAvailable());
        assertEquals(2, sku1.qtyTransit());
        assertEquals(30, sku1.qty30());
        assertEquals(14, sku1.recent7());
        assertEquals(14, sku1.prior7());
        // 毛利回填:300/1000 = 0.3000;商品名随启用商品映射带入
        assertEquals(new BigDecimal("0.3000"), sku1.margin());
        assertEquals("商品甲", sku1.productName());
        assertTrue(sku1.flags().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroSalesButStockedSkuKeptAndMarginMissingFlagged() throws Exception {
        // sku1 有库存零动销 → 保留(滞销候选);利润排行未覆盖 → MARGIN_MISSING 标记
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenReturn(QueryPage.of(List.of(stock(1L, 9, 0)), 1));
        stubGoods(100L, "商品甲", 1L);
        when(salesQueryApi.sumQtyBySku(anyCollection(), anyInt())).thenReturn(Map.of(1L, 0));
        when(salesQueryApi.listDailyQtyBySku(anyCollection(), anyInt())).thenReturn(Map.of());
        when(profitQueryApi.listSkuProfitRank(any(), anyInt())).thenReturn(List.of());

        Map<String, Object> result = node.apply(state());
        List<SelectionCandidate> candidates =
                (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_CANDIDATES);
        assertEquals(1, candidates.size());
        assertEquals(0, candidates.get(0).qty30());
        assertEquals(9, candidates.get(0).qtyAvailable());
        assertTrue(candidates.get(0).flags().contains(SelectionCandidate.FLAG_MARGIN_MISSING));
        org.junit.jupiter.api.Assertions.assertNull(candidates.get(0).margin());
    }

    @Test
    @SuppressWarnings("unchecked")
    void salesBatchFailureDegradesToZeroSalesWithoutAbort() throws Exception {
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenReturn(QueryPage.of(List.of(stock(1L, 5, 0)), 1));
        stubGoods(100L, "商品甲", 1L);
        when(salesQueryApi.sumQtyBySku(anyCollection(), anyInt()))
                .thenThrow(new RuntimeException("db down"));
        when(salesQueryApi.listDailyQtyBySku(anyCollection(), anyInt()))
                .thenThrow(new RuntimeException("db down"));
        when(profitQueryApi.listSkuProfitRank(any(), anyInt())).thenReturn(List.of());

        Map<String, Object> result = node.apply(state());
        List<SelectionCandidate> candidates =
                (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_CANDIDATES);
        // 销量面失败降级零销量:有库存候选保留(不中止),缺数据标记就位
        assertEquals(1, candidates.size());
        assertEquals(0, candidates.get(0).qty30());
        assertTrue(candidates.get(0).flags().contains(SelectionCandidate.FLAG_MARGIN_MISSING));
    }

    @Test
    @SuppressWarnings("unchecked")
    void inventoryScanFailureYieldsEmptyRound() throws Exception {
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenThrow(new RuntimeException("db down"));

        Map<String, Object> result = node.apply(state());
        assertEquals(0, result.get(SelectionStateKeys.KEY_SCANNED));
        assertTrue(((List<?>) result.get(SelectionStateKeys.KEY_CANDIDATES)).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingSuggestionDedupesBySkuId() throws Exception {
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenReturn(QueryPage.of(List.of(stock(1L, 5, 0), stock(2L, 5, 0)), 2))
                .thenReturn(QueryPage.of(List.of(), 0));
        stubGoods(100L, "商品甲", 1L, 2L);
        when(salesQueryApi.sumQtyBySku(anyCollection(), anyInt()))
                .thenReturn(Map.of(1L, 30, 2L, 30));
        Map<Long, Map<LocalDate, Integer>> bothSeries = new java.util.HashMap<>();
        bothSeries.putAll(series(1L, 2, 2));
        bothSeries.putAll(series(2L, 2, 2));
        when(salesQueryApi.listDailyQtyBySku(anyCollection(), anyInt())).thenReturn(bothSeries);
        when(profitQueryApi.listSkuProfitRank(any(), anyInt()))
                .thenReturn(List.of(new ProfitSkuRankRow(1L, "商品甲", 5, 30,
                        new BigDecimal("1000"), new BigDecimal("400"),
                        BigDecimal.ZERO, new BigDecimal("300")),
                        new ProfitSkuRankRow(2L, "商品甲", 5, 30,
                                new BigDecimal("800"), new BigDecimal("200"),
                                BigDecimal.ZERO, new BigDecimal("200"))));
        when(aiSuggestionService.findPendingRefIds(AiConsts.TYPE_SELECTION, AiConsts.REF_TYPE_GOODS_SKU))
                .thenReturn(Set.of(2L));

        Map<String, Object> result = node.apply(state());
        List<SelectionCandidate> candidates =
                (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_CANDIDATES);
        assertEquals(1, candidates.size());
        assertEquals(1L, candidates.get(0).skuId());
    }
}
