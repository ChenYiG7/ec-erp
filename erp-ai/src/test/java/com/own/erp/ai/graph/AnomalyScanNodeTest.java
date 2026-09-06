package com.own.erp.ai.graph;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.QueryPage;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AnomalyScanNode 单测(#6,AIR:mock 契约/fixed Clock 钉死时间/mock 建议服务):
 *     四规则命中/边界、paidTime 判空防 0 元单误报、汇率缺省按 1、多规则合并取 max、
 *     空页即停、scanMaxRows 单态钳制、单态扫描失败隔离、待确认建议去重;OverAllState 真实装配
 */
class AnomalyScanNodeTest {

    private OrderQueryApi orderQueryApi;
    private ErpAiProperties props;
    private Clock clock;
    private LocalDateTime now;
    private AiSuggestionService aiSuggestionService;
    private AnomalyScanNode node;

    @BeforeEach
    void setUp() {
        orderQueryApi = mock(OrderQueryApi.class);
        props = new ErpAiProperties();
        clock = Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneId.of("UTC"));
        now = LocalDateTime.now(clock);
        aiSuggestionService = mock(AiSuggestionService.class);
        when(aiSuggestionService.findPendingRefIds(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Set.of());
        node = new AnomalyScanNode(orderQueryApi, props, clock, aiSuggestionService);
    }

    private void stubPage(String status, int pageNo, OrderQueryApi.OrderView... rows) {
        when(orderQueryApi.pageOrders(OrderQueryApi.OrderFilter.builder()
                .orderStatus(status).pageNo(pageNo).pageSize(props.getAnomaly().getScanPageSize()).build()))
                .thenReturn(QueryPage.of(List.of(rows), rows.length));
    }

    private OverAllState state() {
        // 裸 OverAllState 不注册策略的键会被丢弃,直测节点须先注册(真实工作流由图装配注册)
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(AnomalyStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.registerKeyAndStrategy(AnomalyStateKeys.KEY_SCANNED, KeyStrategy.REPLACE);
        state.input(Map.of());
        return state;
    }

    private OrderQueryApi.OrderView view(Long id, String status, LocalDateTime orderTime,
                                         LocalDateTime paidTime, String amount, String rate, String discount) {
        return OrderQueryApi.OrderView.builder()
                .id(id).shopId(1L).orderStatus(status).orderTime(orderTime).paidTime(paidTime)
                .currency("USD")
                .exchangeRate(rate == null ? null : new BigDecimal(rate))
                .orderAmount(amount == null ? null : new BigDecimal(amount))
                .discountAmount(discount == null ? null : new BigDecimal(discount))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<AnomalyItem> items(Map<String, Object> result) {
        return (List<AnomalyItem>) result.get(AnomalyStateKeys.KEY_ITEMS);
    }

    @Test
    void bigAmountNeedsBaseCurrencyAboveThreshold() throws Exception {
        // 1500×6=9000 < 10000 不命中;2000×6=12000 ≥ 10000 命中
        stubPage("WAIT_SHIP", 1,
                view(1L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "1500", "6", null),
                view(2L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "2000", "6", null));

        Map<String, Object> result = node.apply(state());

        assertEquals(2, result.get(AnomalyStateKeys.KEY_SCANNED));
        List<AnomalyItem> items = items(result);
        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).orderId());
        assertEquals(List.of(AnomalyRule.BIG_AMOUNT), items.get(0).hitRules());
        assertEquals("MID", items.get(0).baselineRisk());
    }

    @Test
    void bigAmountDefaultsExchangeRateToOne() throws Exception {
        // 汇率缺省按 1(#4 落库口径):20000×1 ≥ 10000 命中
        stubPage("WAIT_SHIP", 1,
                view(1L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "20000", null, null));

        List<AnomalyItem> items = items(node.apply(state()));

        assertEquals(1, items.size());
        assertEquals(List.of(AnomalyRule.BIG_AMOUNT), items.get(0).hitRules());
    }

    @Test
    void unpaidTimeoutBoundary() throws Exception {
        LocalDateTime cutoff = now.minusHours(props.getAnomaly().getUnpaidHours());
        stubPage("WAIT_PAY", 1,
                // 超时前 1 分钟:命中
                view(1L, "WAIT_PAY", cutoff.minusMinutes(1), null, "30", "1", null),
                // 恰好卡在边界:不命中(严格 before)
                view(2L, "WAIT_PAY", cutoff, null, "30", "1", null),
                // 新单:不命中
                view(3L, "WAIT_PAY", now.minusMinutes(5), null, "30", "1", null));

        List<AnomalyItem> items = items(node.apply(state()));

        assertEquals(1, items.size());
        assertEquals(1L, items.get(0).orderId());
        assertEquals(List.of(AnomalyRule.UNPAID_TIMEOUT), items.get(0).hitRules());
        assertEquals("LOW", items.get(0).baselineRisk());
        // 未付超时单 paidTime 为 null,payload 侧金额字段原样带出
        assertEquals("30", items.get(0).orderAmount().toPlainString());
    }

    @Test
    void zeroAmountWithoutPaidTimeNotFlagged() throws Exception {
        // 专列钉死 Amazon Pending 0 元单守卫:paidTime=null 的 0 元单绝不误报
        stubPage("WAIT_SHIP", 1,
                view(1L, "WAIT_SHIP", now.minusDays(1), null, "0", "1", null),
                view(2L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "0", "1", null));

        List<AnomalyItem> items = items(node.apply(state()));

        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).orderId());
        assertEquals(List.of(AnomalyRule.ZERO_AMOUNT), items.get(0).hitRules());
        assertEquals("HIGH", items.get(0).baselineRisk());
    }

    @Test
    void highDiscountBoundary() throws Exception {
        stubPage("WAIT_SHIP", 1,
                // 恰好等于 50%:命中(≥)
                view(1L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "100", "1", "50"),
                // 49.99%:不命中
                view(2L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "100", "1", "49.99"),
                // 无优惠:不命中
                view(3L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "100", "1", null));

        List<AnomalyItem> items = items(node.apply(state()));

        assertEquals(1, items.size());
        assertEquals(1L, items.get(0).orderId());
        assertEquals(List.of(AnomalyRule.HIGH_DISCOUNT), items.get(0).hitRules());
        assertEquals("MID", items.get(0).baselineRisk());
    }

    @Test
    void multiRuleMergeTakesMaxRisk() throws Exception {
        // amount=-100:零元负数(HIGH);discount 0 ≥ -100×0.5=-50:高折扣(MID)→ 合并取 max=HIGH
        stubPage("WAIT_SHIP", 1,
                view(1L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "-100", "1", "0"));

        List<AnomalyItem> items = items(node.apply(state()));

        assertEquals(1, items.size());
        assertEquals(2, items.get(0).hitRules().size());
        assertTrue(items.get(0).hitRules().contains(AnomalyRule.ZERO_AMOUNT));
        assertTrue(items.get(0).hitRules().contains(AnomalyRule.HIGH_DISCOUNT));
        assertEquals("HIGH", items.get(0).baselineRisk());
    }

    @Test
    void emptyPageStopsPaging() throws Exception {
        stubPage("WAIT_PAY", 1);
        stubPage("WAIT_SHIP", 1);

        Map<String, Object> result = node.apply(state());

        assertEquals(0, result.get(AnomalyStateKeys.KEY_SCANNED));
        assertTrue(items(result).isEmpty());
        // 两状态各请求第 1 页即停,无第 2 页
        verify(orderQueryApi, times(2)).pageOrders(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scanMaxRowsClampsPerState() throws Exception {
        props.getAnomaly().setScanPageSize(1);
        props.getAnomaly().setScanMaxRows(2);
        // 每状态:第 1 页 1 行、第 2 页 1 行,第 3 页不得再请求(钳制 2 行/状态)
        stubPage("WAIT_PAY", 1, view(1L, "WAIT_PAY", now.minusMinutes(5), null, "30", "1", null));
        stubPage("WAIT_PAY", 2, view(2L, "WAIT_PAY", now.minusMinutes(5), null, "30", "1", null));
        stubPage("WAIT_SHIP", 1, view(3L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "30", "1", null));
        stubPage("WAIT_SHIP", 2, view(4L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "30", "1", null));

        Map<String, Object> result = node.apply(state());

        assertEquals(4, result.get(AnomalyStateKeys.KEY_SCANNED));
        verify(orderQueryApi, times(4)).pageOrders(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void singleStateFailureIsolated() throws Exception {
        // WAIT_PAY 扫描炸:只记日志隔离,WAIT_SHIP 照常出结果(同 AlertEngine 单规则隔离口径)
        when(orderQueryApi.pageOrders(OrderQueryApi.OrderFilter.builder()
                .orderStatus("WAIT_PAY").pageNo(1).pageSize(props.getAnomaly().getScanPageSize()).build()))
                .thenThrow(new RuntimeException("db down"));
        stubPage("WAIT_SHIP", 1,
                view(1L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "20000", "1", null));

        Map<String, Object> result = node.apply(state());

        assertEquals(1, result.get(AnomalyStateKeys.KEY_SCANNED));
        List<AnomalyItem> items = items(result);
        assertEquals(1, items.size());
        assertEquals(1L, items.get(0).orderId());
        assertFalse(items(result).isEmpty());
    }

    @Test
    void pendingSuggestionDeduped() throws Exception {
        // 去重(2026-09-07 拍板):同单已存在待确认建议即跳过,命中也不重复产出;扫描计数不受影响
        stubPage("WAIT_SHIP", 1,
                view(1L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "20000", "1", null),
                view(2L, "WAIT_SHIP", now.minusDays(1), now.minusHours(20), "20000", "1", null));
        when(aiSuggestionService.findPendingRefIds("ANOMALY", "SHOP_ORDER")).thenReturn(Set.of(1L));

        Map<String, Object> result = node.apply(state());

        assertEquals(2, result.get(AnomalyStateKeys.KEY_SCANNED));
        List<AnomalyItem> items = items(result);
        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).orderId());
    }
}
