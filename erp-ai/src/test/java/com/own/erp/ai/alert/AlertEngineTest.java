package com.own.erp.ai.alert;

import com.own.erp.ai.config.ErpAlertProperties;
import com.own.erp.contract.AftersaleQueryApi;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.SalesQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AlertEngine 单测(#6,AIR:mock 契约接口+固定 Clock,不依赖数据库,docs/07 §10):
 *     五规则命中/未命中、空页即停、scanMaxRows 钳制、单规则异常隔离、明细 topN 截断。
 *     桩用 Filter record 值等价精确匹配(不用 argThat:lambda 匹配器对本场景不可靠且可读性差);
 *     销量契约默认桩为"有动销"让滞销/积压保持安静,规则专测再显式覆盖
 */
class AlertEngineTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
            ZoneId.of("Asia/Shanghai"));

    private InventoryQueryApi inventoryQueryApi;
    private OrderQueryApi orderQueryApi;
    private AftersaleQueryApi aftersaleQueryApi;
    private SalesQueryApi salesQueryApi;
    private ErpAlertProperties props;
    private AlertEngine engine;

    @BeforeEach
    void setUp() {
        inventoryQueryApi = mock(InventoryQueryApi.class);
        orderQueryApi = mock(OrderQueryApi.class);
        aftersaleQueryApi = mock(AftersaleQueryApi.class);
        salesQueryApi = mock(SalesQueryApi.class);
        // 默认"有动销且库存周转健康",滞销/积压两规则静默;规则专测内显式覆盖
        when(salesQueryApi.sumQtyBySku(any(), anyInt()))
                .thenReturn(Map.of(1L, 999, 2L, 999, 3L, 999, 4L, 999));
        props = new ErpAlertProperties();
        props.setScanPageSize(2);
        engine = new AlertEngine(inventoryQueryApi, orderQueryApi, aftersaleQueryApi, salesQueryApi, props, CLOCK);
    }

    private InventoryQueryApi.InventoryView inventory(Long skuId, Integer qtyAvailable) {
        return InventoryQueryApi.InventoryView.builder()
                .skuId(skuId).warehouseId(1L).qtyAvailable(qtyAvailable).qtyOnHand(qtyAvailable).build();
    }

    private OrderQueryApi.OrderView order(String platformOrderId, LocalDateTime orderTime) {
        return OrderQueryApi.OrderView.builder()
                .id(1L).shopId(1L).platform("TAOBAO").platformOrderId(platformOrderId)
                .orderStatus("WAIT_SHIP").orderTime(orderTime).build();
    }

    private AftersaleQueryApi.AftersaleView aftersale(Long shopId, LocalDateTime createdAt) {
        return AftersaleQueryApi.AftersaleView.builder()
                .id(1L).aftersaleNo("AS-1").shopId(shopId).status("REFUNDED").createdAt(createdAt).build();
    }

    /** 引擎实际构造的第 pageNo 页过滤器(record 值等价,与引擎构造逐字段一致) */
    private InventoryQueryApi.InventoryFilter invFilter(int pageNo) {
        return InventoryQueryApi.InventoryFilter.builder().pageNo(pageNo).pageSize(props.getScanPageSize()).build();
    }

    private OrderQueryApi.OrderFilter orderFilter(int pageNo) {
        return OrderQueryApi.OrderFilter.builder()
                .orderStatus("WAIT_SHIP").pageNo(pageNo).pageSize(props.getScanPageSize()).build();
    }

    private AftersaleQueryApi.AftersaleFilter refundFilter(int pageNo) {
        return AftersaleQueryApi.AftersaleFilter.builder()
                .status("REFUNDED").pageNo(pageNo).pageSize(props.getScanPageSize()).build();
    }

    @Test
    void lowStockRuleAggregatesHitsIntoOneEvent() {
        when(inventoryQueryApi.pageInventory(invFilter(1))).thenReturn(QueryPage.of(List.of(
                inventory(1L, 3), inventory(2L, 50)), 2));
        // 满页(2 行 = pageSize)后翻页,页 2 空 = 扫描终止
        when(inventoryQueryApi.pageInventory(invFilter(2))).thenReturn(QueryPage.of(List.of(), 0));

        List<AlertEvent> events = engine.evaluate();

        assertEquals(1, events.size());
        AlertEvent event = events.get(0);
        assertEquals(AlertEvent.TYPE_LOW_STOCK, event.notifyType());
        assertTrue(event.content().contains("sku 1 仓 1 可用 3"));
        // 低库存/滞销/积压三条规则各自扫描库存(单规则隔离,不做共享扫描)
        verify(inventoryQueryApi, times(3)).pageInventory(invFilter(1));
        verify(inventoryQueryApi, times(3)).pageInventory(invFilter(2));
        verify(inventoryQueryApi, times(0)).pageInventory(invFilter(3));
    }

    @Test
    void lowStockNoHitYieldsNoEvent() {
        when(inventoryQueryApi.pageInventory(invFilter(1))).thenReturn(QueryPage.of(List.of(
                inventory(1L, props.getLowStockThreshold() + 1), inventory(2L, 99)), 2));

        assertTrue(engine.evaluate().isEmpty());
    }

    @Test
    void emptyPageTerminatesScan() {
        when(inventoryQueryApi.pageInventory(invFilter(1))).thenReturn(
                QueryPage.of(List.of(inventory(1L, 3), inventory(2L, 4)), 2));
        when(inventoryQueryApi.pageInventory(invFilter(2))).thenReturn(QueryPage.of(List.of(), 0));

        engine.evaluate();

        // 低库存/滞销/积压三条规则各自扫描库存,空页后各自终止,第 3 页无人触达
        verify(inventoryQueryApi, times(3)).pageInventory(invFilter(1));
        verify(inventoryQueryApi, times(3)).pageInventory(invFilter(2));
        verify(inventoryQueryApi, times(0)).pageInventory(invFilter(3));
    }

    @Test
    void scanMaxRowsCapsPagination() {
        // scanMaxRows=1000、scanPageSize=2:最多翻 500 页;mock 永远满页,验证 500 页即停
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class))).thenReturn(
                QueryPage.of(List.of(inventory(1L, 3), inventory(2L, 3)), 9999));

        engine.evaluate();

        // 每条库存扫描规则都翻到 500 页封顶,第 501 页无人触达
        verify(inventoryQueryApi, atLeastOnce()).pageInventory(invFilter(500));
        verify(inventoryQueryApi, times(0)).pageInventory(invFilter(501));
    }

    @Test
    void shipTimeoutRuleOnlyCountsOrdersPastDeadline() {
        when(orderQueryApi.pageOrders(orderFilter(1))).thenReturn(QueryPage.of(List.of(
                // orderTime 早于 now-48h 命中
                order("PO-OLD", NOW.minusHours(49)),
                // 阈值内不命中
                order("PO-NEW", NOW.minusHours(47))), 2));
        when(orderQueryApi.pageOrders(orderFilter(2))).thenReturn(QueryPage.of(List.of(), 0));

        List<AlertEvent> events = engine.evaluate();

        assertEquals(1, events.size());
        AlertEvent event = events.get(0);
        assertEquals(AlertEvent.TYPE_SHIP_TIMEOUT, event.notifyType());
        assertTrue(event.content().contains("PO-OLD"));
        assertTrue(!event.content().contains("PO-NEW"));
    }

    @Test
    void refundRuleAggregatesPerShopWithinWindow() {
        // 店铺 1 窗口内 5 单(达阈值),店铺 2 只 1 单,店铺 1 末单窗口外不计;末页 1 行不足页终止
        when(aftersaleQueryApi.pageAftersales(refundFilter(1))).thenReturn(QueryPage.of(List.of(
                aftersale(1L, NOW.minusHours(1)), aftersale(1L, NOW.minusHours(2))), 2));
        when(aftersaleQueryApi.pageAftersales(refundFilter(2))).thenReturn(QueryPage.of(List.of(
                aftersale(1L, NOW.minusHours(3)), aftersale(1L, NOW.minusHours(4))), 2));
        when(aftersaleQueryApi.pageAftersales(refundFilter(3))).thenReturn(QueryPage.of(List.of(
                aftersale(1L, NOW.minusHours(5)), aftersale(2L, NOW.minusHours(6))), 2));
        when(aftersaleQueryApi.pageAftersales(refundFilter(4))).thenReturn(QueryPage.of(List.of(
                aftersale(1L, NOW.minusHours(props.getRefundWindowHours() + 1))), 1));

        List<AlertEvent> events = engine.evaluate();

        assertEquals(1, events.size());
        AlertEvent event = events.get(0);
        assertEquals(AlertEvent.TYPE_REFUND_ABNORMAL, event.notifyType());
        assertTrue(event.content().contains("店铺 1 退款 5 单"));
        assertTrue(!event.content().contains("店铺 2"));
    }

    @Test
    void ruleFailureIsolatedFromOtherRules() {
        // 库存契约抛异常 → 低库存/滞销/积压三条规则跳过,发货超时规则照常产出
        when(inventoryQueryApi.pageInventory(any(InventoryQueryApi.InventoryFilter.class)))
                .thenThrow(new RuntimeException("db down"));
        when(orderQueryApi.pageOrders(orderFilter(1))).thenReturn(
                QueryPage.of(List.of(order("PO-OLD", NOW.minusHours(72))), 1));

        List<AlertEvent> events = engine.evaluate();

        assertEquals(1, events.size());
        assertEquals(AlertEvent.TYPE_SHIP_TIMEOUT, events.get(0).notifyType());
    }

    @Test
    void slowMovingRuleFlagsStockedSkuWithoutSales() {
        // 有库存(avail>阈值避开低库存)但窗口内零销量 → 滞销;两者皆命中
        when(inventoryQueryApi.pageInventory(invFilter(1))).thenReturn(QueryPage.of(List.of(
                inventory(1L, 500), inventory(2L, 600)), 2));
        when(inventoryQueryApi.pageInventory(invFilter(2))).thenReturn(QueryPage.of(List.of(), 0));
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of());

        List<AlertEvent> events = engine.evaluate();

        assertEquals(1, events.size());
        AlertEvent event = events.get(0);
        assertEquals(AlertEvent.TYPE_SLOW_MOVING, event.notifyType());
        assertTrue(event.content().contains("近 30 天零动销且有库存 SKU 共 2 条"));
        assertTrue(event.content().contains("sku 1 仓 1 可用 500"));
    }

    @Test
    void slowMovingRuleIgnoresSkuWithSales() {
        // 窗口内有销量的 SKU 不判滞销(sku1 销量调大避开积压阈值:500 可用/10 天周转 = 50 天 < 90)
        when(inventoryQueryApi.pageInventory(invFilter(1))).thenReturn(QueryPage.of(List.of(
                inventory(1L, 500), inventory(2L, 600)), 2));
        when(inventoryQueryApi.pageInventory(invFilter(2))).thenReturn(QueryPage.of(List.of(), 0));
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 300));

        List<AlertEvent> events = engine.evaluate();

        assertEquals(1, events.size());
        assertTrue(events.get(0).content().contains("sku 2"));
        assertTrue(!events.get(0).content().contains("sku 1 "));
    }

    @Test
    void overstockRuleFlagsHighCoverageOnly() {
        // sku1 可用1000、30 天卖 30(1/天)→ 覆盖 1000 天 ≥90 积压;sku2 可用50 → 覆盖 50 天 <90 不命中
        when(inventoryQueryApi.pageInventory(invFilter(1))).thenReturn(QueryPage.of(List.of(
                inventory(1L, 1000), inventory(2L, 50)), 2));
        when(inventoryQueryApi.pageInventory(invFilter(2))).thenReturn(QueryPage.of(List.of(), 0));
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 30, 2L, 30));

        List<AlertEvent> events = engine.evaluate();

        assertEquals(1, events.size());
        AlertEvent event = events.get(0);
        assertEquals(AlertEvent.TYPE_OVERSTOCK, event.notifyType());
        assertTrue(event.content().contains("sku 1"));
        assertTrue(event.content().contains("≈1000 天"));
        assertTrue(!event.content().contains("sku 2"));
    }

    @Test
    void detailCappedAtTopNWithSuffix() {
        props.setTopN(2);
        when(inventoryQueryApi.pageInventory(invFilter(1))).thenReturn(QueryPage.of(List.of(
                inventory(1L, 1), inventory(2L, 2)), 2));
        when(inventoryQueryApi.pageInventory(invFilter(2))).thenReturn(QueryPage.of(List.of(
                inventory(3L, 3), inventory(4L, 4)), 2));
        when(inventoryQueryApi.pageInventory(invFilter(3))).thenReturn(QueryPage.of(List.of(), 0));

        String content = engine.evaluate().get(0).content();

        // 4 条命中只展示 topN=2 条明细,以"等"收尾
        assertTrue(content.contains("sku 1") && content.contains("sku 2"));
        assertTrue(!content.contains("sku 3"));
        assertTrue(content.endsWith("等"));
    }
}
