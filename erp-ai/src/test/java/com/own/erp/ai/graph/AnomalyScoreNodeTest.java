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
 * @Date : 2026/9/7
 * @Description : AnomalyScoreNode 单测(#6,AIR:mock ChatClient 链路,不出网):
 *     无 key/调用失败/解析失败三重降级规则回落 + 围栏容错按 orderId 对齐 +
 *     词表外/漏回逐单回落 + llmMaxItems 基线风险降序截断且未送评单不算降级;
 *     OverAllState 真实装配,prompt 链显式桩(同 ReplenishSummarizeNodeTest 口径)
 */
class AnomalyScoreNodeTest {

    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec spec;
    private AnomalyScoreNode node;

    @BeforeEach
    void setUp() {
        props = new ErpAiProperties();
        runtime = RuntimePropsStub.of(props);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(spec);
        node = new AnomalyScoreNode(builder, runtime);
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

    private OverAllState stateOf(AnomalyItem... items) {
        // 裸 OverAllState 不注册策略的键会被丢弃,直测节点须先注册(真实工作流由图装配注册)
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(AnomalyStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(AnomalyStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(AnomalyStateKeys.KEY_LLM_SCORED, KeyStrategy.REPLACE);
        state.input(Map.of(AnomalyStateKeys.KEY_ITEMS, List.of(items)));
        return state;
    }

    private AnomalyItem item(Long orderId, String risk) {
        return AnomalyItem.builder()
                .orderId(orderId).shopId(1L)
                .hitRules(List.of(AnomalyRule.UNPAID_TIMEOUT))
                .baselineRisk(risk)
                .currency("USD").orderAmount(new BigDecimal("30"))
                .summary("")
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<AnomalyItem> items(Map<String, Object> result) {
        return (List<AnomalyItem>) result.get(AnomalyStateKeys.KEY_ITEMS);
    }

    @Test
    void blankApiKeyFallsBackToRuleLevelAndMarksDegraded() throws Exception {
        ReflectionTestUtils.setField(node, "apiKey", " ");

        Map<String, Object> result = node.apply(stateOf(item(1L, "HIGH")));

        assertTrue((Boolean) result.get(AnomalyStateKeys.KEY_DEGRADED));
        assertEquals(0, result.get(AnomalyStateKeys.KEY_LLM_SCORED));
        List<AnomalyItem> items = items(result);
        assertFalse(items.get(0).llmScored());
        assertEquals("HIGH", items.get(0).baselineRisk());
        assertEquals("命中规则 未支付超时,基线风险 HIGH,建议人工复核", items.get(0).summary());
    }

    @Test
    void llmInvocationFailureFallsBack() throws Exception {
        stubLlmReply(null);

        Map<String, Object> result = node.apply(stateOf(item(1L, "MID")));

        assertTrue((Boolean) result.get(AnomalyStateKeys.KEY_DEGRADED));
        assertEquals(0, result.get(AnomalyStateKeys.KEY_LLM_SCORED));
        assertEquals("MID", items(result).get(0).baselineRisk());
    }

    @Test
    void unparseableReplyFallsBack() throws Exception {
        stubLlmReply("抱歉,我无法以 JSON 格式回答");

        Map<String, Object> result = node.apply(stateOf(item(1L, "LOW")));

        assertTrue((Boolean) result.get(AnomalyStateKeys.KEY_DEGRADED));
        assertEquals(0, result.get(AnomalyStateKeys.KEY_LLM_SCORED));
        assertEquals("LOW", items(result).get(0).baselineRisk());
    }

    @Test
    void fencedJsonReplyAlignedByOrderId() throws Exception {
        stubLlmReply("```json\n[{\"orderId\":2,\"riskLevel\":\"LOW\",\"reason\":\"小额待发货风险低\"},"
                + "{\"orderId\":1,\"riskLevel\":\"HIGH\",\"reason\":\"零元单疑似刷单\"}]\n```");

        Map<String, Object> result = node.apply(stateOf(item(1L, "HIGH"), item(2L, "MID")));

        assertFalse((Boolean) result.get(AnomalyStateKeys.KEY_DEGRADED));
        assertEquals(2, result.get(AnomalyStateKeys.KEY_LLM_SCORED));
        List<AnomalyItem> items = items(result);
        // 回传顺序与扫描原序无关,输出保持扫描原序 [1,2]
        assertEquals(1L, items.get(0).orderId());
        assertEquals("零元单疑似刷单", items.get(0).summary());
        assertTrue(items.get(0).llmScored());
        assertEquals("LOW", items.get(1).baselineRisk());
        assertEquals("小额待发货风险低", items.get(1).summary());
        assertTrue(items.get(1).llmScored());
    }

    @Test
    void missingOrOutOfVocabFallsBackPerOrder() throws Exception {
        // order1 正常定级;order2 词表外;order3 漏回 → 各自规则回落,选中的单回落计降级
        stubLlmReply("[{\"orderId\":1,\"riskLevel\":\"HIGH\",\"reason\":\"风险高\"},"
                + "{\"orderId\":2,\"riskLevel\":\"SUPER\",\"reason\":\"词表外\"}]");

        Map<String, Object> result = node.apply(
                stateOf(item(1L, "HIGH"), item(2L, "MID"), item(3L, "LOW")));

        assertTrue((Boolean) result.get(AnomalyStateKeys.KEY_DEGRADED));
        assertEquals(1, result.get(AnomalyStateKeys.KEY_LLM_SCORED));
        List<AnomalyItem> items = items(result);
        assertTrue(items.get(0).llmScored());
        assertEquals("风险高", items.get(0).summary());
        // 词表外:回落基线风险 MID + 模板
        assertFalse(items.get(1).llmScored());
        assertEquals("MID", items.get(1).baselineRisk());
        assertTrue(items.get(1).summary().contains("命中规则"));
        // 漏回:回落基线风险 LOW + 模板
        assertFalse(items.get(2).llmScored());
        assertEquals("LOW", items.get(2).baselineRisk());
    }

    @Test
    void truncationSendsOnlyTopRiskAndNotDegraded() throws Exception {
        props.getAnomaly().setLlmMaxItems(1);
        stubLlmReply("[{\"orderId\":2,\"riskLevel\":\"HIGH\",\"reason\":\"大额高风险\"}]");

        // order1=MID / order2=HIGH:降序截断只送 order2,order1 未送评不算降级
        Map<String, Object> result = node.apply(stateOf(item(1L, "MID"), item(2L, "HIGH")));

        assertFalse((Boolean) result.get(AnomalyStateKeys.KEY_DEGRADED));
        assertEquals(1, result.get(AnomalyStateKeys.KEY_LLM_SCORED));
        List<AnomalyItem> items = items(result);
        assertEquals(2, items.size());
        // order1 未送评:规则定级 + 模板,llmScored=false
        assertFalse(items.get(0).llmScored());
        assertEquals("MID", items.get(0).baselineRisk());
        assertTrue(items.get(0).summary().contains("命中规则"));
        // order2 送评采纳:LLM 定级 + 理由
        assertTrue(items.get(1).llmScored());
        assertEquals("HIGH", items.get(1).baselineRisk());
        assertEquals("大额高风险", items.get(1).summary());
    }
}
