package com.own.erp.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : CopyPersistNode 单测(#17):落库字段映射(type=COPYWRITING/refType=GOODS_PRODUCT/
 *     refId=productId/summary=title/risk 恒 LOW/状态待确认)+ payloadJson 文案四件字段序;
 *     skuId/shopId 不落(商品级建议)。OverAllState 真实装配
 */
class CopyPersistNodeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiSuggestionService aiSuggestionService;
    private CopyPersistNode node;

    @BeforeEach
    void setUp() {
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        node = new CopyPersistNode(aiSuggestionService);
    }

    private OverAllState stateOf(CopyItem... items) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(CopyStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(CopyStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE);
        state.input(Map.of(CopyStateKeys.KEY_ITEMS, List.of(items)));
        return state;
    }

    @Test
    void persistsSuggestionWithCopyPayload() throws Exception {
        CopyItem item = CopyItem.builder()
                .productId(7L).spuCode("SPU-7").productName("保温杯")
                .attrsJson(null)
                .skus(List.of())
                .title("品牌 保温杯 大容量便携")
                .bulletPoints(List.of("304不锈钢", "24小时保温"))
                .description("一款优质的保温杯。")
                .keywords(List.of("保温杯", "水杯"))
                .build();

        Map<String, Object> result = node.apply(stateOf(item));

        assertEquals(1, result.get(CopyStateKeys.KEY_PERSISTED));
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        assertEquals("COPYWRITING", saved.getSuggestionType());
        assertEquals("GOODS_PRODUCT", saved.getRefType());
        assertEquals(7L, saved.getRefId());
        assertNull(saved.getSkuId());
        assertNull(saved.getShopId());
        assertEquals("品牌 保温杯 大容量便携", saved.getSummary());
        assertEquals("LOW", saved.getRiskLevel());
        assertEquals(0, saved.getStatus());
        // payload 文案四件(字段序 LinkedHashMap,断言键序)
        JsonNode payload = MAPPER.readTree(saved.getPayloadJson());
        assertEquals(7L, payload.get("productId").asLong());
        assertEquals("保温杯", payload.get("productName").asText());
        assertEquals("品牌 保温杯 大容量便携", payload.get("title").asText());
        assertEquals(2, payload.get("bulletPoints").size());
        assertEquals("一款优质的保温杯。", payload.get("description").asText());
        assertEquals("保温杯", payload.get("keywords").get(0).asText());
        assertTrue(payload.get("bulletPoints").isArray());
    }

    @Test
    void persistsEachGeneratedItem() {
        CopyItem a = CopyItem.builder().productId(1L).productName("A").title("T1")
                .description("D1").build();
        CopyItem b = CopyItem.builder().productId(2L).productName("B").title("T2")
                .description("D2").build();

        Map<String, Object> result = node.apply(stateOf(a, b));

        assertEquals(2, result.get(CopyStateKeys.KEY_PERSISTED));
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(List.of("T1", "T2"), captor.getAllValues().stream()
                .map(AiSuggestion::getSummary).toList());
    }
}
