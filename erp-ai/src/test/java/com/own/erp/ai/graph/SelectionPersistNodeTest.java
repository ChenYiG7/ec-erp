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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SelectionPersistNode 单测(#17 智能选品,AIR):save 委托与四元组
 *     (type/refType/refId/skuId)断言 + payloadJson 评分明细字段回放 + 风险等级与摘要透传
 */
class SelectionPersistNodeTest {

    private AiSuggestionService aiSuggestionService;
    private SelectionPersistNode node;

    @BeforeEach
    void setUp() {
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        node = new SelectionPersistNode(aiSuggestionService);
    }

    private OverAllState stateOf(SelectionCandidate... candidates) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(SelectionStateKeys.KEY_SELECTED, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(SelectionStateKeys.KEY_PERSISTED, KeyStrategy.REPLACE);
        state.input(Map.of(SelectionStateKeys.KEY_SELECTED, List.of(candidates)));
        return state;
    }

    @Test
    void persistsWithScoreDetailPayload() throws Exception {
        SelectionCandidate candidate = SelectionCandidate.builder()
                .skuId(7L).skuCode("SKU-7").productName("测试商品")
                .qtyAvailable(8).qtyTransit(2)
                .qty30(120).recent7(14).prior7(7)
                .salesCny(new BigDecimal("1000")).profitCny(new BigDecimal("185"))
                .margin(new BigDecimal("0.1850"))
                .salesDim(new BigDecimal("100.0")).trendDim(new BigDecimal("100.0"))
                .marginDim(new BigDecimal("61.7"))
                .score(new BigDecimal("87.5")).risk(AiConsts.RISK_LOW)
                .flags(List.of()).summary("销量翻倍且毛利健康").llmScored(true)
                .build();

        Map<String, Object> result = node.apply(stateOf(candidate));

        assertEquals(1, result.get(SelectionStateKeys.KEY_PERSISTED));
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService, times(1)).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        assertEquals(AiConsts.TYPE_SELECTION, saved.getSuggestionType());
        assertEquals(AiConsts.REF_TYPE_GOODS_SKU, saved.getRefType());
        assertEquals(7L, saved.getRefId());
        assertEquals(7L, saved.getSkuId());
        assertEquals(AiConsts.RISK_LOW, saved.getRiskLevel());
        assertEquals(AiConsts.STATUS_PENDING, saved.getStatus());
        assertEquals("销量翻倍且毛利健康", saved.getSummary());
        // payload 全量评分明细可回放(评分可复算拍板的落地面)
        String payload = saved.getPayloadJson();
        assertTrue(payload.contains("\"skuId\":7"));
        assertTrue(payload.contains("\"skuCode\":\"SKU-7\""));
        assertTrue(payload.contains("\"score\":\"87.5\""));
        assertTrue(payload.contains("\"sales\":\"100.0\""));
        assertTrue(payload.contains("\"trend\":\"100.0\""));
        assertTrue(payload.contains("\"margin\":\"61.7\""));
        assertTrue(payload.contains("\"margin\":\"0.1850\""));
        assertTrue(payload.contains("\"qty30\":120"));
        assertTrue(payload.contains("\"recent7\":14"));
        assertTrue(payload.contains("\"prior7\":7"));
        assertTrue(payload.contains("\"qtyAvailable\":8"));
        assertTrue(payload.contains("\"llmScored\":true"));
    }

    @Test
    void missingMarginSerializesNullsAndFlags() throws Exception {
        SelectionCandidate candidate = SelectionCandidate.builder()
                .skuId(9L).skuCode("SKU-9").productName("滞销商品")
                .qtyAvailable(30).qtyTransit(0)
                .qty30(0).recent7(0).prior7(0)
                .margin(null)
                .salesDim(BigDecimal.ZERO.setScale(1)).trendDim(new BigDecimal("50.0"))
                .marginDim(new BigDecimal("50.0"))
                .score(new BigDecimal("15.0")).risk(AiConsts.RISK_MID)
                .flags(List.of(SelectionCandidate.FLAG_MARGIN_MISSING,
                        SelectionCandidate.FLAG_NO_SALES))
                .summary("综合评分 15.0").llmScored(false)
                .build();

        node.apply(stateOf(candidate));

        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        String payload = saved.getPayloadJson();
        // 数据缺口序列化为 null 不猜值;标记随 payload 供人工判读
        assertTrue(payload.contains("\"salesCny\":null"));
        assertTrue(payload.contains("\"margin\":null"));
        assertTrue(payload.contains("MARGIN_MISSING"));
        assertTrue(payload.contains("NO_SALES"));
        assertEquals(AiConsts.RISK_MID, saved.getRiskLevel());
    }
}
