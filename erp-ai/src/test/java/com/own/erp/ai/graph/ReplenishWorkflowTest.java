package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.SalesQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : ReplenishWorkflow 冒烟测试(#6,AIR:真实组装 SAA graph(不启 Spring 上下文),
 *     节点依赖手工注入 mock):空库存走条件边直达 END 零落库;有低库存走全链路落库;
 *     无 apiKey 时摘要降级 degraded=true。销量契约按 V2 逐日序列桩定(sku1 30 天均匀 2 件/天)
 */
class ReplenishWorkflowTest {

    private InventoryQueryApi inventoryQueryApi;
    private SalesQueryApi salesQueryApi;
    private AiSuggestionService aiSuggestionService;
    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private ReplenishWorkflow workflow;

    @BeforeEach
    void setUp() {
        inventoryQueryApi = mock(InventoryQueryApi.class);
        salesQueryApi = mock(SalesQueryApi.class);
        // V2 逐日序列桩:sku1 窗口 30 天均匀 2 件/天(σ=0 退化纯均值口径,期望值可手算)
        Map<LocalDate, Integer> series = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            series.put(LocalDate.of(2026, 8, 10).plusDays(i), 2);
        }
        when(salesQueryApi.listDailyQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, series));
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        props = new ErpAiProperties();
        props.getReplenish().setScanPageSize(2);
        runtime = RuntimePropsStub.of(props);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        // 无 key:summarize 走模板降级链路(真实节点,非 mock)
        ReplenishSummarizeNode summarizeNode = new ReplenishSummarizeNode(builder, runtime);
        ReflectionTestUtils.setField(summarizeNode, "apiKey", "");

        // 共享组件真实装配(2026-09-08 抽取):扫描/计算逻辑单一来源,契约 mock 注入组件
        workflow = new ReplenishWorkflow(
                new ReplenishCollectNode(new LowStockScanner(inventoryQueryApi), props, runtime, aiSuggestionService),
                new ReplenishCalculateNode(new ReplenishCalculator(salesQueryApi), runtime),
                summarizeNode,
                new ReplenishPersistNode(aiSuggestionService));
    }

    private InventoryQueryApi.InventoryFilter filter(int pageNo) {
        return InventoryQueryApi.InventoryFilter.builder()
                .pageNo(pageNo).pageSize(props.getReplenish().getScanPageSize()).build();
    }

    private InventoryQueryApi.InventoryView row(Long skuId, Long warehouseId, Integer available, Integer transit) {
        return InventoryQueryApi.InventoryView.builder()
                .skuId(skuId).warehouseId(warehouseId).qtyAvailable(available).qtyTransit(transit).build();
    }

    @Test
    void emptyInventoryRoutesToEndWithoutPersisting() {
        when(inventoryQueryApi.pageInventory(filter(1))).thenReturn(QueryPage.of(List.of(), 0));

        ReplenishRunResult result = workflow.run();

        assertEquals(0, result.scannedCount());
        assertEquals(0, result.suggestedCount());
        assertEquals(0, result.persistedCount());
        // 条件边直达 END,summarize/persist 未触达
        verifyNoInteractions(aiSuggestionService);
    }

    @Test
    void lowStockFlowsThroughAllNodesAndPersists() {
        when(inventoryQueryApi.pageInventory(filter(1))).thenReturn(QueryPage.of(List.of(
                row(1L, 1L, 3, 0), row(2L, 1L, 50, 0)), 2));
        when(inventoryQueryApi.pageInventory(filter(2))).thenReturn(QueryPage.of(List.of(), 0));

        ReplenishRunResult result = workflow.run();

        assertEquals(2, result.scannedCount());
        assertEquals(1, result.suggestedCount());
        assertEquals(1, result.persistedCount());
        // 无 apiKey:摘要降级但工作流照跑照落库
        assertTrue(result.degraded());
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        assertEquals(AiConsts.TYPE_REPLENISH, saved.getSuggestionType());
        assertEquals(1L, saved.getSkuId());
        // V2 均匀动销:SS=0,ROP=14≥IP=3 触发,S=ceil(2×21)=42 → 建议 42−3=39
        assertTrue(saved.getSummary().contains("建议补货 39"));
        assertTrue(saved.getPayloadJson().contains("39"));
        assertTrue(saved.getPayloadJson().contains("REORDER_POINT_V2"));
        assertEquals(AiConsts.RISK_MID, saved.getRiskLevel());
    }
}
