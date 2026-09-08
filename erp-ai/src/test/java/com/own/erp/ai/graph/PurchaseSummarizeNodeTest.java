package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
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

import java.math.BigDecimal;
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
 * @Date : 2026/9/8
 * @Description : PurchaseSummarizeNode 单测(#17,AIR:mock ChatClient 链路,不出网):
 *     无 key/调用失败/解析失败三重降级落模板 + LLM 正常回传按 supplierId 对齐 + llm-max-items
 *     截断护栏(超限组按预估金额降序淘汰走模板);OverAllState 真实装配
 */
class PurchaseSummarizeNodeTest {

    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec spec;
    private PurchaseSummarizeNode node;

    @BeforeEach
    void setUp() {
        props = new ErpAiProperties();
        runtime = RuntimePropsStub.of(props);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(spec);
        node = new PurchaseSummarizeNode(builder, runtime);
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

    private PurchaseGroup group(Long supplierId, String name, int totalQty, String estAmount,
                                boolean hasStockout) {
        return PurchaseGroup.builder()
                .supplierId(supplierId).supplierName(name)
                .totalQty(totalQty).estAmount(new BigDecimal(estAmount))
                .hasStockout(hasStockout)
                .lines(List.of(PurchaseGroup.Line.builder()
                        .skuId(1L).suggestQty(totalQty).lastPrice(new BigDecimal("10"))
                        .estAmount(new BigDecimal(estAmount)).qtyAvailable(hasStockout ? 0 : 3).build()))
                .summary("")
                .build();
    }

    private OverAllState stateOf(PurchaseGroup... groups) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(PurchaseStateKeys.KEY_GROUPS, KeyStrategy.REPLACE);
        state.input(Map.of(PurchaseStateKeys.KEY_GROUPS, List.of(groups)));
        return state;
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankApiKeyFallsBackToTemplateAndMarksDegraded() throws Exception {
        ReflectionTestUtils.setField(node, "apiKey", " ");

        Map<String, Object> result = node.apply(stateOf(group(10L, "供应商甲", 28, "350.00", true)));

        assertTrue((Boolean) result.get(PurchaseStateKeys.KEY_DEGRADED));
        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertTrue(groups.get(0).summary().contains("供应商甲"));
        assertTrue(groups.get(0).summary().contains("350.00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmJsonArrayIsAlignedBySupplierId() throws Exception {
        stubLlmReply("[{\"supplierId\":10,\"summary\":\"缺货风险高,建议本周内下单\"}]");

        Map<String, Object> result = node.apply(stateOf(group(10L, "供应商甲", 28, "350.00", true)));

        assertFalse((Boolean) result.get(PurchaseStateKeys.KEY_DEGRADED));
        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertEquals("缺货风险高,建议本周内下单", groups.get(0).summary());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fencedJsonReplyIsStrippedAndParsed() throws Exception {
        stubLlmReply("```json\n[{\"supplierId\":10,\"summary\":\"建议补 28 件保 14 天覆盖\"}]\n```");

        Map<String, Object> result = node.apply(stateOf(group(10L, "供应商甲", 28, "350.00", false)));

        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertEquals("建议补 28 件保 14 天覆盖", groups.get(0).summary());
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmInvocationFailureFallsBackToTemplate() throws Exception {
        stubLlmReply(null);

        Map<String, Object> result = node.apply(stateOf(group(10L, "供应商甲", 28, "350.00", false)));

        assertTrue((Boolean) result.get(PurchaseStateKeys.KEY_DEGRADED));
        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertTrue(groups.get(0).summary().contains("28"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmMaxItemsTruncatesLowestAmountGroupsToTemplate() throws Exception {
        // 护栏:超限组按预估金额降序淘汰,走模板但不阻断(两组,上限 1 → 金额小的乙组模板)
        props.getPurchase().setLlmMaxItems(1);
        stubLlmReply("[{\"supplierId\":20,\"summary\":\"金额最高,优先采购\"}]");

        Map<String, Object> result = node.apply(stateOf(
                group(20L, "供应商乙", 10, "800.00", false),
                group(10L, "供应商甲", 28, "350.00", true)));

        assertTrue((Boolean) result.get(PurchaseStateKeys.KEY_DEGRADED));
        List<PurchaseGroup> groups = (List<PurchaseGroup>) result.get(PurchaseStateKeys.KEY_GROUPS);
        assertEquals("金额最高,优先采购", groups.get(0).summary());
        assertTrue(groups.get(1).summary().contains("供应商甲"));
    }
}
