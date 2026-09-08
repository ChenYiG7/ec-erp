package com.own.erp.finance.service;

import com.own.erp.finance.mapper.RefundReconciliationMapper;
import com.own.erp.finance.reconcile.RefundDiffEvent;
import com.own.erp.finance.reconcile.RefundReconciliationAlert;
import com.own.erp.finance.reconcile.RefundSideRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : RefundReconciliationService 单测(#19④,AIR:mock Mapper,不依赖数据库):
 *     差异判定三类全分支(金额不符含容差边界/结算侧缺失/币种混算双侧)/勾稽平不告警/
 *     多行归并(跨报告/多售后单 SUM)/跨店铺同订单号隔离/聚合告警装配(topN 截断声明总笔数)
 */
class RefundReconciliationServiceTest {

    private static final Long SHOP_ID = 7L;

    private RefundReconciliationMapper mapper;
    private RefundReconciliationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RefundReconciliationMapper.class);
        service = new RefundReconciliationService(mapper);
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(List.of());
        when(mapper.sumSettlementRefundByPlatformOrder()).thenReturn(List.of());
    }

    private static RefundSideRow aftersale(String order, String currency, String amount) {
        return new RefundSideRow(SHOP_ID, order, currency, new BigDecimal(amount));
    }

    private static RefundSideRow settlement(String order, String currency, String amount) {
        return new RefundSideRow(SHOP_ID, order, currency, new BigDecimal(amount));
    }

    @Test
    void emptyDataProducesNoAlert() {
        assertNull(service.reconcileAlert());
        assertTrue(service.reconcile().isEmpty());
    }

    @Test
    void balancedWithinToleranceProducesNoAlert() {
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(List.of(aftersale("111-222", "USD", "29.99")));
        // 结算侧跨报告两行自然 SUM = 29.99,勾稽平
        when(mapper.sumSettlementRefundByPlatformOrder()).thenReturn(List.of(
                settlement("111-222", "USD", "20.00"), settlement("111-222", "USD", "9.99")));

        assertNull(service.reconcileAlert());
        assertTrue(service.reconcile().isEmpty());
    }

    @Test
    void differenceAtExactToleranceCountsAsBalanced() {
        // 差额恰等于容差 0.01(|差|>容差才告警):防 DECIMAL(18,2) vs DECIMAL(12,4) 舍入尾差误报的边界
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(List.of(aftersale("111-222", "USD", "29.99")));
        when(mapper.sumSettlementRefundByPlatformOrder()).thenReturn(List.of(settlement("111-222", "USD", "29.98")));

        assertNull(service.reconcileAlert());
    }

    @Test
    void amountMismatchProducesAlertWithBothSidesAndDiff() {
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(List.of(aftersale("111-222", "USD", "59.98")));
        when(mapper.sumSettlementRefundByPlatformOrder()).thenReturn(List.of(settlement("111-222", "USD", "29.99")));

        RefundReconciliationAlert alert = service.reconcileAlert();

        assertEquals(RefundReconciliationService.NOTIFY_TYPE_REFUND_DIFF, alert.notifyType());
        assertEquals("退款勾稽差异告警", alert.title());
        assertEquals(1, alert.diffCount());
        RefundDiffEvent event = service.reconcile().get(0);
        assertEquals(RefundDiffEvent.TYPE_AMOUNT_MISMATCH, event.diffType());
        assertEquals(SHOP_ID, event.shopId());
        assertEquals("111-222", event.platformOrderId());
        assertEquals(0, new BigDecimal("59.98").compareTo(event.aftersaleAmount()));
        assertEquals(0, new BigDecimal("29.99").compareTo(event.settlementAmount()));
        assertEquals(0, new BigDecimal("29.99").compareTo(event.diffAmount()));
        assertEquals("USD", event.aftersaleCurrency());
        assertEquals("USD", event.settlementCurrency());
    }

    @Test
    void missingInSettlementProducesAlertWithoutSettlementAmount() {
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(List.of(aftersale("111-333", "USD", "29.99")));
        // 结算侧只有其他订单的 REFUND 行
        when(mapper.sumSettlementRefundByPlatformOrder()).thenReturn(List.of(settlement("111-444", "USD", "10.00")));

        RefundDiffEvent event = service.reconcile().get(0);

        assertEquals(RefundDiffEvent.TYPE_MISSING_IN_SETTLEMENT, event.diffType());
        assertEquals("111-333", event.platformOrderId());
        assertEquals(0, new BigDecimal("29.99").compareTo(event.aftersaleAmount()));
        assertNull(event.settlementAmount());
        assertNull(event.diffAmount());
        assertEquals("USD", event.aftersaleCurrency());
    }

    @Test
    void currencyMismatchBetweenSidesProducesAlertWithoutAmountCompare() {
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(List.of(aftersale("111-222", "USD", "29.99")));
        when(mapper.sumSettlementRefundByPlatformOrder()).thenReturn(List.of(settlement("111-222", "EUR", "29.99")));

        RefundDiffEvent event = service.reconcile().get(0);

        assertEquals(RefundDiffEvent.TYPE_CURRENCY_MISMATCH, event.diffType());
        assertEquals("USD", event.aftersaleCurrency());
        assertEquals("EUR", event.settlementCurrency());
        // 币种不符禁混币计算:双侧金额与差额不产出,禁猜
        assertNull(event.aftersaleAmount());
        assertNull(event.settlementAmount());
        assertNull(event.diffAmount());
    }

    @Test
    void mixedCurrencyWithinAftersaleSideProducesAlert() {
        // 同订单售后侧多币种(脏数据):即使结算侧单币种也不比金额,直接币种差异留痕
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(List.of(
                aftersale("111-222", "USD", "29.99"), aftersale("111-222", "EUR", "20.00")));
        when(mapper.sumSettlementRefundByPlatformOrder()).thenReturn(List.of(settlement("111-222", "USD", "29.99")));

        RefundDiffEvent event = service.reconcile().get(0);

        assertEquals(RefundDiffEvent.TYPE_CURRENCY_MISMATCH, event.diffType());
        assertEquals("EUR,USD", event.aftersaleCurrency());
        assertEquals("USD", event.settlementCurrency());
    }

    @Test
    void samePlatformOrderAcrossShopsDoNotCrossMatch() {
        RefundSideRow shopA = new RefundSideRow(7L, "111-222", "USD", new BigDecimal("29.99"));
        RefundSideRow shopB = new RefundSideRow(8L, "111-222", "USD", new BigDecimal("29.99"));
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(List.of(shopA));
        // 店铺 8 的同号订单在结算侧有行,不得与店铺 7 的售后侧勾稽(归集键含店铺)
        when(mapper.sumSettlementRefundByPlatformOrder()).thenReturn(List.of(shopB));

        RefundDiffEvent event = service.reconcile().get(0);

        assertEquals(RefundDiffEvent.TYPE_MISSING_IN_SETTLEMENT, event.diffType());
        assertEquals(7L, event.shopId());
    }

    @Test
    void contentDeclaresTotalAndTruncatesToTopN() {
        List<RefundSideRow> aftersaleRows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            aftersaleRows.add(aftersale("111-9%02d".formatted(i), "USD", "29.99"));
        }
        when(mapper.sumAftersaleRefundByPlatformOrder()).thenReturn(aftersaleRows);

        RefundReconciliationAlert alert = service.reconcileAlert();

        assertEquals(25, alert.diffCount());
        String[] lines = alert.content().lines().toArray(String[]::new);
        assertEquals("共 25 笔退款勾稽差异(仅列前 20 笔):", lines[0]);
        assertEquals(21, lines.length);
        assertTrue(lines[1].startsWith("shop=7 订单 111-900 [MISSING_IN_SETTLEMENT]"));
    }
}
