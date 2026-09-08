package com.own.erp.ai.graph;

import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : PurchasePersistNode 单测(#17,AIR:mock Service):
 *     逐组 save 字段装配(PURCHASE/SUPPLIER/refId/skuId=NULL/风险分级/待确认态)+
 *     payloadJson 结构(组级汇总 + 行明细)断言
 */
class PurchasePersistNodeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiSuggestionService aiSuggestionService;
    private PurchasePersistNode node;

    @BeforeEach
    void setUp() {
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        node = new PurchasePersistNode(aiSuggestionService);
    }

    private PurchaseGroup group(Long supplierId, String name, boolean hasStockout, String estAmount) {
        return PurchaseGroup.builder()
                .supplierId(supplierId).supplierName(name)
                .totalQty(28).estAmount(new BigDecimal(estAmount))
                .hasStockout(hasStockout)
                .lines(List.of(PurchaseGroup.Line.builder()
                        .skuId(1L).suggestQty(28).lastPrice(new BigDecimal("12.50"))
                        .estAmount(new BigDecimal(estAmount)).qtyAvailable(hasStockout ? 0 : 3).build()))
                .summary("建议采购 28 件")
                .build();
    }

    @Test
    void persistsEveryGroupWithRiskGradingAndPayload() throws Exception {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(PurchaseStateKeys.KEY_GROUPS, KeyStrategy.REPLACE);
        state.input(Map.of(PurchaseStateKeys.KEY_GROUPS, List.of(
                group(10L, "供应商甲", true, "350.00"),
                group(20L, "供应商乙", false, "80.00"))));

        Object persisted = node.apply(state).get(PurchaseStateKeys.KEY_PERSISTED);

        assertEquals(2, persisted);
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService, times(2)).save(captor.capture());
        List<AiSuggestion> rows = captor.getAllValues();

        AiSuggestion first = rows.get(0);
        assertEquals(AiConsts.TYPE_PURCHASE, first.getSuggestionType());
        assertEquals("SUPPLIER", first.getRefType());
        assertEquals(10L, first.getRefId());
        assertNull(first.getSkuId());
        assertEquals(AiConsts.RISK_HIGH, first.getRiskLevel());
        assertEquals(AiConsts.STATUS_PENDING, first.getStatus());
        assertTrue(first.getSummary().contains("28 件"));

        // payloadJson 结构:组级汇总 + 行明细(JSON 列,解析断言禁脆弱字符串包含)
        JsonNode payload = objectMapper.readTree(first.getPayloadJson());
        assertEquals(10L, payload.get("supplierId").asLong());
        assertEquals("供应商甲", payload.get("supplierName").asText());
        assertEquals(28, payload.get("totalQty").asInt());
        assertEquals(0, new BigDecimal("350.00").compareTo(new BigDecimal(payload.get("estAmount").asText())));
        assertEquals(1, payload.get("lines").size());
        assertEquals(1L, payload.get("lines").get(0).get("skuId").asLong());
        assertEquals(28, payload.get("lines").get(0).get("suggestQty").asInt());

        assertEquals(AiConsts.RISK_MID, rows.get(1).getRiskLevel());
        assertEquals(20L, rows.get(1).getRefId());
    }
}
