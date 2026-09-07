package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.QueryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AnomalyWorkflow 冒烟测试(#6,AIR:真实组装 SAA graph(不启 Spring 上下文),
 *     节点依赖手工注入 mock,fixed Clock 钉死时间):无可疑单走条件边直达 END 零落库;
 *     有可疑单走全链路落库;无 apiKey 时评分降级规则回落且 degraded 传播
 */
class AnomalyWorkflowTest {

    private OrderQueryApi orderQueryApi;
    private AiSuggestionService aiSuggestionService;
    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private AnomalyWorkflow workflow;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        orderQueryApi = mock(OrderQueryApi.class);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.save(any())).thenReturn(1L);
        props = new ErpAiProperties();
        runtime = RuntimePropsStub.of(props);

        Clock clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneId.of("UTC"));
        now = LocalDateTime.now(clock);

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        // 无 key:score 走规则回落链路(真实节点,非 mock)
        AnomalyScoreNode scoreNode = new AnomalyScoreNode(builder, runtime);
        ReflectionTestUtils.setField(scoreNode, "apiKey", "");

        workflow = new AnomalyWorkflow(
                new AnomalyScanNode(orderQueryApi, props, runtime, clock, aiSuggestionService),
                scoreNode,
                new AnomalyPersistNode(aiSuggestionService));
    }

    private void stubPage(String status, int pageNo, OrderQueryApi.OrderView... rows) {
        when(orderQueryApi.pageOrders(OrderQueryApi.OrderFilter.builder()
                .orderStatus(status).pageNo(pageNo).pageSize(props.getAnomaly().getScanPageSize()).build()))
                .thenReturn(QueryPage.of(List.of(rows), rows.length));
    }

    @Test
    void noSuspiciousRoutesToEndWithoutPersisting() {
        // 正常已支付小额单:两态各一页,均不命中
        stubPage("WAIT_PAY", 1, OrderQueryApi.OrderView.builder()
                .id(1L).shopId(1L).orderStatus("WAIT_PAY").orderTime(now.minusMinutes(5))
                .currency("USD").orderAmount(new BigDecimal("30")).build());
        stubPage("WAIT_SHIP", 1, OrderQueryApi.OrderView.builder()
                .id(2L).shopId(1L).orderStatus("WAIT_SHIP").orderTime(now.minusDays(1))
                .paidTime(now.minusHours(20))
                .currency("USD").orderAmount(new BigDecimal("30")).build());

        AnomalyRunResult result = workflow.run();

        assertEquals(2, result.scannedCount());
        assertEquals(0, result.suspiciousCount());
        assertEquals(0, result.persistedCount());
        assertEquals(0, result.llmScoredCount());
        // 条件边直达 END,score/persist 未触达
        verifyNoInteractions(aiSuggestionService);
    }

    @Test
    void fullFlowPersistsWithDegradedPropagation() {
        // 超时未付单(72h > 48h)命中 UNPAID_TIMEOUT
        stubPage("WAIT_PAY", 1, OrderQueryApi.OrderView.builder()
                .id(9L).shopId(3L).orderStatus("WAIT_PAY").orderTime(now.minusHours(72))
                .currency("USD").orderAmount(new BigDecimal("30")).build());
        stubPage("WAIT_SHIP", 1);

        AnomalyRunResult result = workflow.run();

        assertEquals(1, result.scannedCount());
        assertEquals(1, result.suspiciousCount());
        assertEquals(1, result.persistedCount());
        // 无 apiKey:评分降级走规则回落,llmScored 不计,degraded 传播到出参
        assertEquals(0, result.llmScoredCount());
        assertTrue(result.degraded());
        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionService).save(captor.capture());
        AiSuggestion saved = captor.getValue();
        assertEquals(AiConsts.TYPE_ANOMALY, saved.getSuggestionType());
        assertEquals("SHOP_ORDER", saved.getRefType());
        assertEquals(9L, saved.getRefId());
        assertEquals(3L, saved.getShopId());
        assertEquals(AiConsts.RISK_LOW, saved.getRiskLevel());
        assertTrue(saved.getSummary().contains("未支付超时"));
        assertTrue(saved.getPayloadJson().contains("\"hitRules\":[\"UNPAID_TIMEOUT\"]"));
        assertTrue(saved.getPayloadJson().contains("\"llmScored\":false"));
    }
}
