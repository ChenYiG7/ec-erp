package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.QueryPage;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : CopyCollectNode 单测(#17,AIR:mock 契约,不出网):
 *     启用商品扫描+材料装配断言 / 同商品待确认建议去重跳过 / 单商品材料查询失败隔离不殃及整轮 /
 *     SKU 行截前 20 条护栏。OverAllState 真实装配(同 AnomalyScanNodeTest 口径)
 */
class CopyCollectNodeTest {

    private GoodsQueryApi goodsQueryApi;
    private AiSuggestionService aiSuggestionService;
    private ErpAiProperties props;
    private CopyCollectNode node;

    @BeforeEach
    void setUp() {
        goodsQueryApi = mock(GoodsQueryApi.class);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.findPendingRefIds(any(), any())).thenReturn(Set.of());
        props = new ErpAiProperties();
        props.getCopy().setScanPageSize(2);
        node = new CopyCollectNode(goodsQueryApi, props, aiSuggestionService);
    }

    private OverAllState state() {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(CopyStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(CopyStateKeys.KEY_SCANNED, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(CopyStateKeys.KEY_SKIPPED, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(CopyStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(CopyStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE);
        state.input(Map.of());
        return state;
    }

    private GoodsQueryApi.ProductView product(Long id, String name) {
        return GoodsQueryApi.ProductView.builder()
                .id(id).spuCode("SPU-" + id).name(name).categoryId(11L).brandId(12L)
                .attrsJson("{\"材质\":\"不锈钢\"}").status(1).build();
    }

    private GoodsQueryApi.SkuView sku(Long id, String code) {
        return GoodsQueryApi.SkuView.builder()
                .id(id).productId(1L).skuCode(code).attrsJson("{\"颜色\":\"红\"}")
                .weightG(350).battery(0).status(1).build();
    }

    /** 按页码打桩(页 1 返回 rows,页 2 起空页——防 mock 同页无限翻页撞护栏) */
    private void stubPages(GoodsQueryApi.ProductView... rows) {
        List<GoodsQueryApi.ProductView> rowList = List.of(rows);
        when(goodsQueryApi.pageProducts(any())).thenAnswer(inv -> {
            GoodsQueryApi.ProductFilter filter = inv.getArgument(0);
            return filter.pageNo() == 1 ? QueryPage.of(rowList, rowList.size())
                    : QueryPage.of(List.of(), 0);
        });
    }

    @Test
    void scansEnabledProductsAndAssemblesMaterial() {
        stubPages(product(1L, "保温杯"), product(2L, "雨伞"));
        when(goodsQueryApi.listSkusByProductId(1L)).thenReturn(List.of(sku(101L, "A01"), sku(102L, "A02")));
        when(goodsQueryApi.listSkusByProductId(2L)).thenReturn(List.of());
        when(goodsQueryApi.findBrandNameById(12L)).thenReturn("某品牌");
        when(goodsQueryApi.findCategoryNameById(11L)).thenReturn("家居");

        Map<String, Object> result = node.apply(state());

        assertEquals(2, result.get(CopyStateKeys.KEY_SCANNED));
        assertEquals(0, result.get(CopyStateKeys.KEY_SKIPPED));
        @SuppressWarnings("unchecked")
        List<CopyItem> items = (List<CopyItem>) result.get(CopyStateKeys.KEY_ITEMS);
        assertEquals(2, items.size());
        CopyItem first = items.get(0);
        assertEquals(1L, first.productId());
        assertEquals("保温杯", first.productName());
        assertEquals("某品牌", first.brandName());
        assertEquals("家居", first.categoryName());
        assertEquals("{\"材质\":\"不锈钢\"}", first.attrsJson());
        assertEquals(2, first.skus().size());
        assertEquals("A01", first.skus().get(0).skuCode());
        assertEquals(350, first.skus().get(0).weightG());
        // 无 SKU 商品:空行列表照常进生成队列
        assertEquals(0, items.get(1).skus().size());
    }

    @Test
    void dedupSkipsProductsWithPendingSuggestion() {
        stubPages(product(1L, "保温杯"), product(2L, "雨伞"));
        when(aiSuggestionService.findPendingRefIds(any(), any())).thenReturn(Set.of(2L));

        Map<String, Object> result = node.apply(state());

        assertEquals(2, result.get(CopyStateKeys.KEY_SCANNED));
        assertEquals(1, result.get(CopyStateKeys.KEY_SKIPPED));
        @SuppressWarnings("unchecked")
        List<CopyItem> items = (List<CopyItem>) result.get(CopyStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        assertEquals(1L, items.get(0).productId());
    }

    @Test
    void materialFailureIsolatesSingleProduct() {
        stubPages(product(1L, "保温杯"), product(2L, "雨伞"));
        // 商品 1 材料查询炸,商品 2 无 SKU 正常:只跳过商品 1,不殃及整轮
        when(goodsQueryApi.listSkusByProductId(1L)).thenThrow(new RuntimeException("db down"));
        when(goodsQueryApi.listSkusByProductId(2L)).thenReturn(List.of());
        when(goodsQueryApi.findBrandNameById(anyLong())).thenReturn("某品牌");
        when(goodsQueryApi.findCategoryNameById(anyLong())).thenReturn("家居");

        Map<String, Object> result = node.apply(state());

        assertEquals(2, result.get(CopyStateKeys.KEY_SCANNED));
        @SuppressWarnings("unchecked")
        List<CopyItem> items = (List<CopyItem>) result.get(CopyStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        // 存活的是材料装配成功的商品 2(品牌/类目正常回填),商品 1 整体跳过
        assertEquals(2L, items.get(0).productId());
        assertEquals("某品牌", items.get(0).brandName());
        assertEquals("家居", items.get(0).categoryName());
    }

    @Test
    void truncatesSkuLinesToPromptMax() {
        stubPages(product(1L, "多规格商品"));
        List<GoodsQueryApi.SkuView> many = new java.util.ArrayList<>();
        for (int i = 1; i <= CopyCollectNode.SKU_PROMPT_MAX + 10; i++) {
            many.add(sku((long) i, "S" + i));
        }
        when(goodsQueryApi.listSkusByProductId(1L)).thenReturn(many);

        Map<String, Object> result = node.apply(state());

        @SuppressWarnings("unchecked")
        List<CopyItem> items = (List<CopyItem>) result.get(CopyStateKeys.KEY_ITEMS);
        assertEquals(CopyCollectNode.SKU_PROMPT_MAX, items.get(0).skus().size());
        assertTrue(items.get(0).skus().stream().allMatch(line -> line.skuCode().startsWith("S")));
    }
}
