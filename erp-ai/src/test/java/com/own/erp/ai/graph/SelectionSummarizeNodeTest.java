package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.common.constant.ConfigConsts;
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
import java.util.ArrayList;
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
 * @Description : SelectionSummarizeNode 单测(#17 智能选品,AIR:mock ChatClient 链路,不出网):
 *     无 key 全模板降级 + LLM 正常回传按 skuId 对齐 + 调用失败降级 + llm-max-items 截断护栏
 *     (未送评/漏回行走模板,degraded 标记与采购摘要同口径);OverAllState 真实装配
 */
class SelectionSummarizeNodeTest {

    private ErpAiProperties props;
    private ChatClient.Builder builder;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec spec;
    private SelectionSummarizeNode node;

    @BeforeEach
    void setUp() {
        props = new ErpAiProperties();
        builder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(spec);
        node = nodeWithApiKey("test-key", RuntimePropsStub.of(props));
    }

    private SelectionSummarizeNode nodeWithApiKey(String apiKey, AiRuntimeProperties runtime) {
        SelectionSummarizeNode n = new SelectionSummarizeNode(builder, runtime);
        ReflectionTestUtils.setField(n, "apiKey", apiKey);
        return n;
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

    /** 入选行 fixture(score 已由评分节点回填,摘要节点按该值出模板) */
    private SelectionCandidate candidate(Long skuId, int qty30, String margin) {
        return SelectionCandidate.builder()
                .skuId(skuId).skuCode("C" + skuId).productName("商品" + skuId)
                .qtyAvailable(10).qtyTransit(0)
                .qty30(qty30).recent7(qty30 / 10).prior7(qty30 / 10)
                .salesCny(margin == null ? null : new BigDecimal("1000"))
                .profitCny(margin == null ? null : new BigDecimal(margin).multiply(new BigDecimal("1000")))
                .margin(margin == null ? null : new BigDecimal(margin))
                .score(new BigDecimal("85.0"))
                .flags(margin == null
                        ? new ArrayList<>(List.of(SelectionCandidate.FLAG_MARGIN_MISSING))
                        : new ArrayList<>())
                .summary("").llmScored(false)
                .build();
    }

    private OverAllState stateOf(SelectionCandidate... candidates) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(SelectionStateKeys.KEY_SELECTED, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(SelectionStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE);
        state.input(Map.of(SelectionStateKeys.KEY_SELECTED, List.of(candidates)));
        return state;
    }

    @Test
    @SuppressWarnings("unchecked")
    void noApiKeyFallsBackToTemplateAndMarksDegraded() throws Exception {
        SelectionSummarizeNode noKeyNode = nodeWithApiKey("", RuntimePropsStub.of(props));
        Map<String, Object> result = noKeyNode.apply(stateOf(candidate(1L, 120, "0.185")));
        List<SelectionCandidate> selected =
                (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertTrue((Boolean) result.get(SelectionStateKeys.KEY_DEGRADED));
        assertEquals("综合评分 85.0:近30天销量 120 件,毛利率 18.5%,可用库存 10 件",
                selected.get(0).summary());
        assertFalse(selected.get(0).llmScored());
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmReplyAlignedBySkuId() throws Exception {
        stubLlmReply("[{\"skuId\":1,\"summary\":\"销量稳定且毛利健康,建议加大推广\"}]");
        Map<String, Object> result = node.apply(stateOf(candidate(1L, 120, "0.185")));
        List<SelectionCandidate> selected =
                (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertFalse((Boolean) result.get(SelectionStateKeys.KEY_DEGRADED));
        assertEquals("销量稳定且毛利健康,建议加大推广", selected.get(0).summary());
        assertTrue(selected.get(0).llmScored());
    }

    @Test
    @SuppressWarnings("unchecked")
    void callFailureFallsBackToTemplate() throws Exception {
        stubLlmReply(null);
        Map<String, Object> result = node.apply(stateOf(candidate(1L, 0, null)));
        List<SelectionCandidate> selected =
                (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertTrue((Boolean) result.get(SelectionStateKeys.KEY_DEGRADED));
        // 缺毛利模板:毛利率"未知"占位,禁猜值
        assertEquals("综合评分 85.0:近30天销量 0 件,毛利率 未知,可用库存 10 件",
                selected.get(0).summary());
        assertFalse(selected.get(0).llmScored());
    }

    @Test
    @SuppressWarnings("unchecked")
    void llmMaxItemsTruncatesRestToTemplate() throws Exception {
        // 送评护栏 1:入选集已按综合分降序,只送第 1 行;第 2 行走模板(截断仍产出建议,不阻断)
        stubLlmReply("[{\"skuId\":1,\"summary\":\"第一优先\"}]");
        SelectionSummarizeNode limited = nodeWithApiKey("test-key", RuntimePropsStub.of(props, Map.of(
                ConfigConsts.KEY_SELECTION_LLM_MAX_ITEMS, "1")));
        Map<String, Object> result = limited.apply(
                stateOf(candidate(1L, 200, "0.30"), candidate(2L, 100, "0.30")));
        List<SelectionCandidate> selected =
                (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertTrue((Boolean) result.get(SelectionStateKeys.KEY_DEGRADED));
        assertEquals("第一优先", selected.get(0).summary());
        assertTrue(selected.get(0).llmScored());
        assertEquals("综合评分 85.0:近30天销量 100 件,毛利率 30.0%,可用库存 10 件",
                selected.get(1).summary());
        assertFalse(selected.get(1).llmScored());
    }
}
