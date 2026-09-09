package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.ProfitQueryApi;
import com.own.erp.contract.ProfitSkuRankRow;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.SalesQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SelectionWorkflow 冒烟测试(#17 智能选品,AIR:真实组装 SAA graph(不启 Spring 上下文),
 *     节点依赖手工注入 mock):零候选走条件边直达 END 零落库零 LLM;有候选走全链落库;
 *     无 apiKey 摘要降级 degraded=true;固定时钟保证销量窗口断言确定
 */
class SelectionWorkflowTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-08T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private InventoryQueryApi inventoryQueryApi;
    private GoodsQueryApi goodsQueryApi;
    private SalesQueryApi salesQueryApi;
    private ProfitQueryApi profitQueryApi;
    private AiSuggestionService aiSuggestionService;
    private ErpAiProperties props;
    private SelectionWorkflow workflow;

    @BeforeEach
    void setUp() {
        inventoryQueryApi = mock(InventoryQueryApi.class);
        goodsQueryApi = mock(GoodsQueryApi.class);
        salesQueryApi = mock(SalesQueryApi.class);
        profitQueryApi = mock(ProfitQueryApi.class);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        when(aiSuggestionService.findPendingRefIds(any(), any())).thenReturn(Set.of());
        props = new ErpAiProperties();
        props.getSelection().setScanPageSize(10);
    }

    private SelectionWorkflow buildWorkflow(String apiKey) {
        AiRuntimeProperties runtime = RuntimePropsStub.of(props);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        SelectionSummarizeNode summarizeNode = new SelectionSummarizeNode(builder, runtime);
        ReflectionTestUtils.setField(summarizeNode, "apiKey", apiKey);
        return new SelectionWorkflow(
                new SelectionCollectNode(inventoryQueryApi, goodsQueryApi, salesQueryApi,
                        profitQueryApi, props, aiSuggestionService, CLOCK),
                new SelectionScoreNode(runtime),
                summarizeNode,
                new SelectionPersistNode(aiSuggestionService));
    }

    private void stubOneHealthySku() {
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenReturn(QueryPage.of(List.of(
                        InventoryQueryApi.InventoryView.builder()
                                .skuId(1L).warehouseId(1L).qtyAvailable(8).qtyTransit(0).build()), 1));
        GoodsQueryApi.ProductView product = GoodsQueryApi.ProductView.builder()
                .id(100L).spuCode("SPU-1").name("测试商品").status(1).build();
        when(goodsQueryApi.pageProducts(any(GoodsQueryApi.ProductFilter.class)))
                .thenReturn(QueryPage.of(List.of(product), 1));
        when(goodsQueryApi.listSkusByProductId(100L)).thenReturn(List.of(
                GoodsQueryApi.SkuView.builder().id(1L).productId(100L).skuCode("SKU-1").status(1).build()));
        Map<LocalDate, Integer> daily = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            daily.put(LocalDate.of(2026, 9, 8).minusDays(i), 4);
        }
        when(salesQueryApi.sumQtyBySku(anyCollection(), anyInt())).thenReturn(Map.of(1L, 120));
        when(salesQueryApi.listDailyQtyBySku(anyCollection(), anyInt()))
                .thenReturn(Map.of(1L, daily));
        when(profitQueryApi.listSkuProfitRank(any(), anyInt()))
                .thenReturn(List.of(new ProfitSkuRankRow(1L, "测试商品", 5, 120,
                        new BigDecimal("6000"), new BigDecimal("2400"),
                        new BigDecimal("-900"), new BigDecimal("2700"))));
    }

    @Test
    void emptyInventoryRoutesToEndWithoutPersisting() {
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenReturn(QueryPage.of(List.of(), 0));

        SelectionRunResult result = buildWorkflow("").run();

        assertEquals(0, result.scannedCount());
        assertEquals(0, result.candidateCount());
        assertEquals(0, result.persistedCount());
        // 条件边直达 END:score/summarize/persist 未触达,零 LLM 成本
        verifyNoInteractions(aiSuggestionService);
    }

    @Test
    void healthySkuFlowsThroughAllNodesAndPersists() {
        stubOneHealthySku();

        SelectionRunResult result = buildWorkflow("").run();

        assertEquals(1, result.scannedCount());
        assertEquals(1, result.candidateCount());
        assertEquals(1, result.selectedCount());
        assertEquals(1, result.persistedCount());
        // 无 apiKey:摘要降级但工作流照跑照落库
        assertTrue(result.degraded());
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        assertEquals(AiConsts.TYPE_SELECTION, saved.getSuggestionType());
        assertEquals(AiConsts.REF_TYPE_GOODS_SKU, saved.getRefType());
        assertEquals(1L, saved.getRefId());
        assertEquals(1L, saved.getSkuId());
        assertEquals(AiConsts.RISK_LOW, saved.getRiskLevel());
        // 均匀动销 4 件/天:近 7 天 = 前 7 天 = 28 → 趋势持平 50 分;毛利 2700/6000 = 0.45 > 0.30 满分;
        // 销量唯一候选 100 分 → 综合分 = 0.4×100 + 0.3×50 + 0.3×100 = 85.0
        assertTrue(saved.getPayloadJson().contains("\"score\":\"85.0\""));
        assertTrue(saved.getPayloadJson().contains("\"qty30\":120"));
        assertTrue(saved.getPayloadJson().contains("\"recent7\":28"));
        assertTrue(saved.getPayloadJson().contains("\"prior7\":28"));
    }

    @Test
    void deadSkuExcludedRoutesToEnd() {
        // 有库存行但零动销零可用(死 SKU)→ 零候选 → 直达 END
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenReturn(QueryPage.of(List.of(
                        InventoryQueryApi.InventoryView.builder()
                                .skuId(1L).warehouseId(1L).qtyAvailable(0).qtyTransit(0).build()), 1));
        GoodsQueryApi.ProductView product = GoodsQueryApi.ProductView.builder()
                .id(100L).spuCode("SPU-1").name("测试商品").status(1).build();
        when(goodsQueryApi.pageProducts(any(GoodsQueryApi.ProductFilter.class)))
                .thenReturn(QueryPage.of(List.of(product), 1));
        when(goodsQueryApi.listSkusByProductId(100L)).thenReturn(List.of(
                GoodsQueryApi.SkuView.builder().id(1L).productId(100L).skuCode("SKU-1").status(1).build()));
        when(salesQueryApi.sumQtyBySku(anyCollection(), anyInt())).thenReturn(Map.of(1L, 0));
        when(salesQueryApi.listDailyQtyBySku(anyCollection(), anyInt())).thenReturn(Map.of());

        SelectionRunResult result = buildWorkflow("test-key").run();

        assertEquals(1, result.scannedCount());
        assertEquals(0, result.candidateCount());
        assertEquals(0, result.persistedCount());
        verify(aiSuggestionService, org.mockito.Mockito.times(0)).save(any());
    }
}
