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
 * @Description : CopyGenerateNode 单测(#17,AIR:mock ChatClient 链路,不出网):
 *     无 key/调用失败/解析失败统一零产出置 degraded(文案无模板兜底,拍板语义)+
 *     围栏容错按 productId 对齐 + 漏回/必填缺失逐商品跳过 +
 *     llmMaxItems 按 productId 升序截断且截断不算降级。OverAllState 真实装配
 */
class CopyGenerateNodeTest {

    private ErpAiProperties props;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec spec;
    private CopyGenerateNode node;

    @BeforeEach
    void setUp() {
        props = new ErpAiProperties();
        AiRuntimeProperties runtime = RuntimePropsStub.of(props);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(spec);
        node = new CopyGenerateNode(builder, runtime);
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

    private OverAllState stateOf(CopyItem... items) {
        // 裸 OverAllState 不注册策略的键会被丢弃,直测节点须先注册(真实工作流由图装配注册)
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(CopyStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(CopyStateKeys.KEY_DEGRADED, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(CopyStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE);
        state.input(Map.of(CopyStateKeys.KEY_ITEMS, List.of(items)));
        return state;
    }

    private CopyItem item(Long productId) {
        return CopyItem.builder()
                .productId(productId).spuCode("SPU-" + productId).productName("商品" + productId)
                .brandName("品牌").categoryName("类目").attrsJson(null)
                .skus(List.of(new CopyItem.SkuLine("A" + productId, "{\"颜色\":\"红\"}", 350, 0)))
                .build();
    }

    private static final String GOOD_JSON = """
            [{"productId":1,"title":"品牌 保温杯 大容量便携","bulletPoints":["304不锈钢","24小时保温"],\
            "description":"一款优质的保温杯。","keywords":["保温杯","水杯"]},\
            {"productId":2,"title":"品牌 折叠雨伞 十骨加大","bulletPoints":["十骨抗风","一秒开收"],\
            "description":"一款结实的折叠雨伞。","keywords":["雨伞","折叠伞"]}]""";

    @Test
    void noApiKeySkipsAllWithDegraded() {
        ReflectionTestUtils.setField(node, "apiKey", "");
        Map<String, Object> result = node.apply(stateOf(item(1L), item(2L)));
        assertTrue(((List<?>) result.get(CopyStateKeys.KEY_ITEMS)).isEmpty());
        assertTrue((Boolean) result.get(CopyStateKeys.KEY_DEGRADED));
    }

    @Test
    void llmFailureSkipsAllWithDegraded() {
        stubLlmReply(null);
        Map<String, Object> result = node.apply(stateOf(item(1L)));
        assertTrue(((List<?>) result.get(CopyStateKeys.KEY_ITEMS)).isEmpty());
        assertTrue((Boolean) result.get(CopyStateKeys.KEY_DEGRADED));
    }

    @Test
    void unparsableReplySkipsAllWithDegraded() {
        stubLlmReply("抱歉,我无法生成文案。");
        Map<String, Object> result = node.apply(stateOf(item(1L)));
        assertTrue(((List<?>) result.get(CopyStateKeys.KEY_ITEMS)).isEmpty());
        assertTrue((Boolean) result.get(CopyStateKeys.KEY_DEGRADED));
    }

    @Test
    void fencedJsonParsesAndAlignsByProductId() {
        stubLlmReply("```json\n" + GOOD_JSON + "\n```");
        Map<String, Object> result = node.apply(stateOf(item(2L), item(1L)));
        assertFalse((Boolean) result.get(CopyStateKeys.KEY_DEGRADED));
        @SuppressWarnings("unchecked")
        List<CopyItem> items = (List<CopyItem>) result.get(CopyStateKeys.KEY_ITEMS);
        assertEquals(2, items.size());
        CopyItem first = items.get(0);
        assertEquals("品牌 保温杯 大容量便携", first.title());
        assertEquals(List.of("304不锈钢", "24小时保温"), first.bulletPoints());
        assertEquals("一款优质的保温杯。", first.description());
        assertEquals(List.of("保温杯", "水杯"), first.keywords());
    }

    @Test
    void missingOptionalFieldsDefaultToEmptyLists() {
        stubLlmReply("[{\"productId\":1,\"title\":\"标题\",\"description\":\"描述\"}]");
        Map<String, Object> result = node.apply(stateOf(item(1L)));
        assertFalse((Boolean) result.get(CopyStateKeys.KEY_DEGRADED));
        @SuppressWarnings("unchecked")
        List<CopyItem> items = (List<CopyItem>) result.get(CopyStateKeys.KEY_ITEMS);
        assertEquals(List.of(), items.get(0).bulletPoints());
        assertEquals(List.of(), items.get(0).keywords());
    }

    @Test
    void missingOrInvalidEntriesSkipWithDegraded() {
        // 商品 2 漏回 + 商品 3 title 缺失,均跳过;商品 1 正常产出
        stubLlmReply("[{\"productId\":1,\"title\":\"标题\",\"description\":\"描述\"},"
                + "{\"productId\":3,\"title\":\"\",\"description\":\"描述\"}]");
        Map<String, Object> result = node.apply(stateOf(item(1L), item(2L), item(3L)));
        assertTrue((Boolean) result.get(CopyStateKeys.KEY_DEGRADED));
        @SuppressWarnings("unchecked")
        List<CopyItem> items = (List<CopyItem>) result.get(CopyStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        assertEquals(1L, items.get(0).productId());
    }

    @Test
    void truncationByProductIdIsNotDegraded() {
        props.getCopy().setLlmMaxItems(1);
        stubLlmReply("[{\"productId\":1,\"title\":\"标题\",\"description\":\"描述\"}]");
        Map<String, Object> result = node.apply(stateOf(item(2L), item(1L)));
        // 截断商品本轮不产出不算降级(下轮扫描自然补上,拍板口径)
        assertFalse((Boolean) result.get(CopyStateKeys.KEY_DEGRADED));
        @SuppressWarnings("unchecked")
        List<CopyItem> items = (List<CopyItem>) result.get(CopyStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        assertEquals(1L, items.get(0).productId());
    }

    @Test
    void emptyInputShortCircuitsWithoutLlm() {
        Map<String, Object> result = node.apply(stateOf());
        assertFalse((Boolean) result.get(CopyStateKeys.KEY_DEGRADED));
        assertTrue(((List<?>) result.get(CopyStateKeys.KEY_ITEMS)).isEmpty());
    }
}
