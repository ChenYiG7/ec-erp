package com.own.erp.ai.graph;

import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AnomalyPersistNode 单测(#6,AIR:mock Service):
 *     逐条 save 字段装配(ANOMALY/SHOP_ORDER/refId/shopId/风险/待确认态/payloadJson 键)与空列表零落库
 */
class AnomalyPersistNodeTest {

    private AiSuggestionService aiSuggestionService;
    private AnomalyPersistNode node;

    @BeforeEach
    void setUp() {
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        node = new AnomalyPersistNode(aiSuggestionService);
    }

    private OverAllState stateOf(List<AnomalyItem> items) {
        // 裸 OverAllState 不注册策略的键会被丢弃,直测节点须先注册(真实工作流由图装配注册)
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(AnomalyStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(AnomalyStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE);
        state.input(Map.of(AnomalyStateKeys.KEY_ITEMS, items));
        return state;
    }

    @Test
    void persistsEveryFieldWithTypeAnomaly() {
        AnomalyItem item = AnomalyItem.builder()
                .orderId(101L).shopId(7L)
                .hitRules(List.of(AnomalyRule.ZERO_AMOUNT, AnomalyRule.HIGH_DISCOUNT))
                .baselineRisk(AiConsts.RISK_HIGH)
                .currency("USD")
                .orderAmount(new BigDecimal("0"))
                .exchangeRate(new BigDecimal("6.5"))
                .discountAmount(new BigDecimal("10"))
                .orderTime(LocalDateTime.of(2026, 9, 6, 10, 0))
                .paidTime(LocalDateTime.of(2026, 9, 6, 10, 5))
                .summary("零元单疑似刷单")
                .llmScored(true)
                .build();

        Object persisted = node.apply(stateOf(List.of(item))).get(AnomalyStateKeys.KEY_PERSISTED);

        assertEquals(1, persisted);
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        assertEquals(AiConsts.TYPE_ANOMALY, saved.getSuggestionType());
        assertEquals("SHOP_ORDER", saved.getRefType());
        assertEquals(101L, saved.getRefId());
        assertEquals(7L, saved.getShopId());
        assertNull(saved.getSkuId());
        assertEquals(AiConsts.RISK_HIGH, saved.getRiskLevel());
        assertEquals("零元单疑似刷单", saved.getSummary());
        assertEquals(AiConsts.STATUS_PENDING, saved.getStatus());
        // payload 键收口拍板口径:hitRules/ruleRisk/金额汇率/时间/llmScored
        assertTrue(saved.getPayloadJson().contains("\"hitRules\":[\"ZERO_AMOUNT\",\"HIGH_DISCOUNT\"]"));
        assertTrue(saved.getPayloadJson().contains("\"ruleRisk\":\"HIGH\""));
        assertTrue(saved.getPayloadJson().contains("\"orderAmount\":\"0\""));
        assertTrue(saved.getPayloadJson().contains("\"currency\":\"USD\""));
        assertTrue(saved.getPayloadJson().contains("\"exchangeRate\":\"6.5\""));
        assertTrue(saved.getPayloadJson().contains("\"discountAmount\":\"10\""));
        assertTrue(saved.getPayloadJson().contains("\"orderTime\":\"2026-09-06T10:00:00\""));
        assertTrue(saved.getPayloadJson().contains("\"paidTime\":\"2026-09-06T10:05:00\""));
        assertTrue(saved.getPayloadJson().contains("\"llmScored\":true"));
    }

    @Test
    void emptyItemsPersistNothing() {
        Object persisted = node.apply(stateOf(List.of())).get(AnomalyStateKeys.KEY_PERSISTED);

        assertEquals(0, persisted);
        verifyNoInteractions(aiSuggestionService);
    }
}
