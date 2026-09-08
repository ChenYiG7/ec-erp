package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.QueryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : CopywritingWorkflow 冒烟测试(#17,AIR:真实组装 SAA graph(不启 Spring 上下文),
 *     节点依赖手工注入 mock):无启用商品走条件边直达 END 零 LLM 零落库;有商品+无 apiKey 走
 *     生成零产出降级;有商品+正常回传走全链路落库。
 */
class CopywritingWorkflowTest {

    private GoodsQueryApi goodsQueryApi;
    private AiSuggestionService aiSuggestionService;
    private ErpAiProperties props;
    private CopywritingWorkflow workflow;
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        goodsQueryApi = mock(GoodsQueryApi.class);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        when(aiSuggestionService.findPendingRefIds(any(), any())).thenReturn(java.util.Set.of());
        props = new ErpAiProperties();
        props.getCopy().setScanPageSize(2);
        AiRuntimeProperties runtime = RuntimePropsStub.of(props);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF));

        CopyGenerateNode generateNode = new CopyGenerateNode(builder, runtime);
        ReflectionTestUtils.setField(generateNode, "apiKey", "");
        workflow = new CopywritingWorkflow(
                new CopyCollectNode(goodsQueryApi, props, aiSuggestionService),
                generateNode,
                new CopyPersistNode(aiSuggestionService));
    }

    private GoodsQueryApi.ProductView product(Long id, String name) {
        return GoodsQueryApi.ProductView.builder()
                .id(id).spuCode("SPU-" + id).name(name).status(1).build();
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
    void emptyProductsRouteToEndWithoutLlmOrPersist() {
        when(goodsQueryApi.pageProducts(any())).thenReturn(QueryPage.of(List.of(), 0));

        CopyRunResult result = workflow.run();

        assertEquals(0, result.scannedCount());
        assertEquals(0, result.generatedCount());
        assertFalse(result.degraded());
        // 去重查询因空商品短路不触库,save 更不应发生
        verifyNoInteractions(aiSuggestionService);
    }

    @Test
    void productsWithNoApiKeyDegradeToZeroOutput() {
        stubPages(product(1L, "保温杯"));

        CopyRunResult result = workflow.run();

        assertEquals(1, result.scannedCount());
        assertEquals(0, result.generatedCount());
        assertTrue(result.degraded());
        // 无 key 零产出:除去重读侧外不落任何建议
        org.mockito.Mockito.verify(aiSuggestionService, org.mockito.Mockito.never())
                .save(any());
    }

    @Test
    void happyPathPersistsSuggestions() {
        stubPages(product(1L, "保温杯"), product(2L, "雨伞"));
        when(goodsQueryApi.listSkusByProductId(any())).thenReturn(List.of());
        when(goodsQueryApi.findBrandNameById(any())).thenReturn(null);
        when(goodsQueryApi.findCategoryNameById(any())).thenReturn(null);
        CopyGenerateNode generateNode = stubGenerateWithReply("[{\"productId\":1,\"title\":\"T1\","
                + "\"description\":\"D1\"},{\"productId\":2,\"title\":\"T2\",\"description\":\"D2\"}]");
        CopywritingWorkflow real = new CopywritingWorkflow(
                new CopyCollectNode(goodsQueryApi, props, aiSuggestionService),
                generateNode,
                new CopyPersistNode(aiSuggestionService));

        CopyRunResult result = real.run();

        assertEquals(2, result.scannedCount());
        assertEquals(2, result.generatedCount());
        assertFalse(result.degraded());
    }

    /** 快乐路径用带 key 的真实生成节点(ReflectionTestUtils 注入 key 并桩回传) */
    private CopyGenerateNode stubGenerateWithReply(String reply) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenAnswer(inv -> {
            org.springframework.ai.chat.model.ChatResponse response =
                    mock(org.springframework.ai.chat.model.ChatResponse.class);
            org.springframework.ai.chat.model.Generation generation =
                    mock(org.springframework.ai.chat.model.Generation.class);
            when(response.getResult()).thenReturn(generation);
            when(generation.getOutput()).thenReturn(
                    new org.springframework.ai.chat.messages.AssistantMessage(reply));
            return response;
        });
        CopyGenerateNode node = new CopyGenerateNode(builder, RuntimePropsStub.of(props));
        ReflectionTestUtils.setField(node, "apiKey", "test-key");
        return node;
    }
}
