package com.own.erp.ai.graph;

import com.own.erp.ai.config.ErpAiProperties;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : ReplenishSummarizeNode 单测(#6,AIR:mock ChatClient 链路,不出网):
 *     无 key/调用失败/解析失败三重降级落模板 + LLM 正常回传按 skuId 对齐;OverAllState 真实装配,
 *     prompt 链显式桩(同 ErpChatServiceTest 口径,不走深桩)
 */
class ReplenishSummarizeNodeTest {

    private ErpAiProperties props;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec spec;
    private ReplenishSummarizeNode node;

    @BeforeEach
    void setUp() {
        props = new ErpAiProperties();
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(spec);
        node = new ReplenishSummarizeNode(builder, props);
        ReflectionTestUtils.setField(node, "apiKey", "test-key");
    }

    private void stubLlmReply(String text) {
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenAnswer(inv -> {
            if (text == null) {
                throw new RuntimeException("network down");
            }
            ChatResponse response = mock(ChatResponse.class);
            Generation generation = mock(Generation.class);
            when(response.getResult()).thenReturn(generation);
            when(generation.getOutput()).thenReturn(new AssistantMessage(text));
            return response;
        });
    }

    private OverAllState stateOf(ReplenishItem... items) {
        // 裸 OverAllState 不注册策略的键会被丢弃,直测节点须先注册(真实工作流由图装配注册)
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(ReplenishStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.input(Map.of(ReplenishStateKeys.KEY_ITEMS, List.of(items)));
        return state;
    }

    private ReplenishItem item(Long skuId) {
        return ReplenishItem.builder().skuId(skuId).qtyAvailable(3).qtyTransit(2).suggestQty(23).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankApiKeyFallsBackToTemplateAndMarksDegraded() throws Exception {
        ReflectionTestUtils.setField(node, "apiKey", " ");

        Map<String, Object> result = node.apply(stateOf(item(1L)));

        assertTrue((Boolean) result.get(ReplenishStateKeys.KEY_DEGRADED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals("SKU 1 可用 3,在途 2,建议补货 23", items.get(0).summary());
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmJsonArrayIsAlignedBySkuId() throws Exception {
        stubLlmReply("[{\"skuId\":1,\"summary\":\"低库存风险,建议补 23 件\"}]");

        Map<String, Object> result = node.apply(stateOf(item(1L)));

        assertFalse((Boolean) result.get(ReplenishStateKeys.KEY_DEGRADED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals("低库存风险,建议补 23 件", items.get(0).summary());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fencedJsonReplyIsStrippedAndParsed() throws Exception {
        stubLlmReply("```json\n[{\"skuId\":1,\"summary\":\"补 23 件保 14 天覆盖\"}]\n```");

        Map<String, Object> result = node.apply(stateOf(item(1L)));

        assertFalse((Boolean) result.get(ReplenishStateKeys.KEY_DEGRADED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals("补 23 件保 14 天覆盖", items.get(0).summary());
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmInvocationFailureFallsBackToTemplate() throws Exception {
        stubLlmReply(null);

        Map<String, Object> result = node.apply(stateOf(item(1L)));

        assertTrue((Boolean) result.get(ReplenishStateKeys.KEY_DEGRADED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals("SKU 1 可用 3,在途 2,建议补货 23", items.get(0).summary());
    }

    @Test
    @SuppressWarnings("unchecked")
    void unparseableReplyFallsBackToTemplate() throws Exception {
        stubLlmReply("抱歉,我无法以 JSON 格式回答");

        Map<String, Object> result = node.apply(stateOf(item(1L)));

        assertTrue((Boolean) result.get(ReplenishStateKeys.KEY_DEGRADED));
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals("SKU 1 可用 3,在途 2,建议补货 23", items.get(0).summary());
    }
}
