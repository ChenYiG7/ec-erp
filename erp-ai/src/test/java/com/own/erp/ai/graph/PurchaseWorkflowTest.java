package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.SalesQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * @Date : 2026/9/8
 * @Description : PurchaseWorkflow 冒烟测试(#17,AIR:真实组装 SAA graph(不启 Spring 上下文),
 *     节点依赖手工注入 mock):无低库存走条件边直达 END 零落库;有缺口+供应商映射走全链路落库;
 *     全部 SKU 无采购历史 → 聚合为空同样直达 END;无 apiKey 时摘要降级 degraded=true
 */
class PurchaseWorkflowTest {

    private InventoryQueryApi inventoryQueryApi;
    private SalesQueryApi salesQueryApi;
    private PurchaseQueryApi purchaseQueryApi;
    private AiSuggestionService aiSuggestionService;
    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private PurchaseWorkflow workflow;

    @BeforeEach
    void setUp() {
        inventoryQueryApi = mock(InventoryQueryApi.class);
        salesQueryApi = mock(SalesQueryApi.class);
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 60));
        purchaseQueryApi = mock(PurchaseQueryApi.class);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        when(aiSuggestionService.findPendingRefIds(any(), any())).thenReturn(java.util.Set.of());
        props = new ErpAiProperties();
        props.getPurchase().setScanPageSize(2);
        runtime = RuntimePropsStub.of(props);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        // 无 key:summarize 走模板降级链路(真实节点,非 mock)
        PurchaseSummarizeNode summarizeNode = new PurchaseSummarizeNode(builder, runtime);
        ReflectionTestUtils.setField(summarizeNode, "apiKey", "");

        workflow = new PurchaseWorkflow(
                new PurchaseCollectNode(new LowStockScanner(inventoryQueryApi),
                        new ReplenishCalculator(salesQueryApi), props, runtime),
                new PurchaseAggregateNode(purchaseQueryApi, aiSuggestionService),
                summarizeNode,
                new PurchasePersistNode(aiSuggestionService));
    }

    private InventoryQueryApi.InventoryFilter filter(int pageNo) {
        return InventoryQueryApi.InventoryFilter.builder()
                .pageNo(pageNo).pageSize(props.getPurchase().getScanPageSize()).build();
    }

    private InventoryQueryApi.InventoryView row(Long skuId, Long warehouseId, Integer available, Integer transit) {
        return InventoryQueryApi.InventoryView.builder()
                .skuId(skuId).warehouseId(warehouseId).qtyAvailable(available).qtyTransit(transit).build();
    }

    @Test
    void emptyInventoryRoutesToEndWithoutPersisting() {
        when(inventoryQueryApi.pageInventory(filter(1))).thenReturn(QueryPage.of(List.of(), 0));

        PurchaseRunResult result = workflow.run();

        assertEquals(0, result.scannedCount());
        assertEquals(0, result.groupCount());
        assertEquals(0, result.persistedCount());
        // 条件边直达 END,summarize/persist 未触达
        verifyNoInteractions(aiSuggestionService);
    }

    @Test
    void lowStockWithSupplierFlowsThroughAllNodesAndPersists() {
        when(inventoryQueryApi.pageInventory(filter(1))).thenReturn(QueryPage.of(List.of(
                row(1L, 1L, 3, 0), row(2L, 1L, 50, 0)), 2));
        when(inventoryQueryApi.pageInventory(filter(2))).thenReturn(QueryPage.of(List.of(), 0));
        when(purchaseQueryApi.findLatestSupplierBySkuIds(any())).thenReturn(List.of(
                PurchaseQueryApi.SkuSupplierView.builder()
                        .skuId(1L).supplierId(10L).supplierName("供应商甲")
                        .lastPrice(new BigDecimal("12.50"))
                        .lastPoNo("PO-1").lastPoAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                        .build()));

        PurchaseRunResult result = workflow.run();

        assertEquals(2, result.scannedCount());
        assertEquals(1, result.suggestedSkuCount());
        assertEquals(0, result.noSupplierCount());
        assertEquals(1, result.groupCount());
        assertEquals(1, result.persistedCount());
        // 无 apiKey:摘要降级但工作流照跑照落库
        assertTrue(result.degraded());
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        assertEquals(AiConsts.TYPE_PURCHASE, saved.getSuggestionType());
        assertEquals("SUPPLIER", saved.getRefType());
        assertEquals(10L, saved.getRefId());
        // calculate: max(10, 14*2-3-0)=25;预估金额 = 12.50×25 = 312.50
        assertTrue(saved.getSummary().contains("供应商甲"));
        assertTrue(saved.getPayloadJson().contains("312.50"));
        assertEquals(AiConsts.RISK_MID, saved.getRiskLevel());
    }

    @Test
    void allSkusWithoutSupplierHistoryRouteToEnd() {
        // 有补货缺口但全部无采购历史 → 聚合为空 → 条件边直达 END(零 LLM 成本)
        when(inventoryQueryApi.pageInventory(filter(1))).thenReturn(QueryPage.of(List.of(
                row(1L, 1L, 3, 0)), 1));
        when(purchaseQueryApi.findLatestSupplierBySkuIds(any())).thenReturn(List.of());

        PurchaseRunResult result = workflow.run();

        assertEquals(1, result.suggestedSkuCount());
        assertEquals(1, result.noSupplierCount());
        assertEquals(0, result.groupCount());
        assertEquals(0, result.persistedCount());
        verify(aiSuggestionService, org.mockito.Mockito.times(0)).save(any());
    }
}
