package com.own.erp.ai.graph;

import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
 * @Date : 2026/9/6
 * @Description : ReplenishPersistNode 单测(#6,AIR:mock Service):
 *     逐条 save 字段装配(REPLENISH/INVENTORY/风险分级/待确认态)、条数回传
 */
class ReplenishPersistNodeTest {

    private AiSuggestionService aiSuggestionService;
    private ReplenishPersistNode node;

    @BeforeEach
    void setUp() {
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        node = new ReplenishPersistNode(aiSuggestionService);
    }

    @Test
    void persistsEveryItemWithRiskGrading() {
        // 裸 OverAllState 不注册策略的键会被丢弃,直测节点须先注册(真实工作流由图装配注册)
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(ReplenishStateKeys.KEY_ITEMS, com.alibaba.cloud.ai.graph.KeyStrategy.REPLACE);
        state.input(Map.of(ReplenishStateKeys.KEY_ITEMS, List.of(
                // 缺货(可用≤0)→ HIGH
                ReplenishItem.builder().skuId(1L).qtyAvailable(0).qtyTransit(0).suggestQty(28)
                        .summary("缺货,补 28").build(),
                // 有库存 → MID
                ReplenishItem.builder().skuId(2L).qtyAvailable(5).qtyTransit(2).suggestQty(18)
                        .summary("低库存,补 18").build())));

        Object persisted = node.apply(state).get(ReplenishStateKeys.KEY_PERSISTED);

        assertEquals(2, persisted);
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService, times(2)).save(captor.capture());
        List<AiSuggestion> rows = captor.getAllValues();
        for (AiSuggestion row : rows) {
            assertEquals(AiConsts.TYPE_REPLENISH, row.getSuggestionType());
            assertEquals("INVENTORY", row.getRefType());
            assertEquals(AiConsts.STATUS_PENDING, row.getStatus());
            assertTrue(row.getPayloadJson().contains("suggestQty"));
        }
        assertEquals(AiConsts.RISK_HIGH, rows.get(0).getRiskLevel());
        assertEquals(1L, rows.get(0).getSkuId());
        assertEquals(AiConsts.RISK_MID, rows.get(1).getRiskLevel());
        assertTrue(rows.get(1).getSummary().contains("补 18"));
    }

    @Test
    void calcJsonPreferredOverLegacyPayload() {
        // V2:calculate 回填的算法明细优先透传 payloadJson(含 μ/σ/补货点/目标库存);
        // 空串回落旧三字段形态(防御,正常不触发)
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(ReplenishStateKeys.KEY_ITEMS, com.alibaba.cloud.ai.graph.KeyStrategy.REPLACE);
        state.input(Map.of(ReplenishStateKeys.KEY_ITEMS, List.of(
                ReplenishItem.builder().skuId(1L).qtyAvailable(0).qtyTransit(0).suggestQty(69)
                        .summary("").calcJson("{\"algorithm\":\"REORDER_POINT_V2\",\"suggestQty\":69}").build(),
                ReplenishItem.builder().skuId(2L).qtyAvailable(5).qtyTransit(2).suggestQty(18)
                        .summary("").calcJson("").build())));

        node.apply(state);

        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().get(0).getPayloadJson().contains("REORDER_POINT_V2"));
        assertTrue(captor.getAllValues().get(1).getPayloadJson().contains("suggestQty"));
    }
}
