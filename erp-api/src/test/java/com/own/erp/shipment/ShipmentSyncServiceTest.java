package com.own.erp.shipment;

import com.own.erp.common.api.DeliveryShippedEvent;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.ShopOrderApi;
import com.own.erp.fulfill.response.DeliveryOrderItemResponse;
import com.own.erp.fulfill.response.DeliveryOrderResponse;
import com.own.erp.fulfill.service.DeliveryOrderService;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.shop.service.PullLogService;
import com.own.erp.shop.service.ShopService;
import com.own.erp.system.service.SysNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : ShipmentSyncService 单测(AIR:mock 协作对象 + 固定时钟,不依赖 DB/Redis/网络),
 *     覆盖 #11 发货回传编排的判定矩阵:
 *     跳过(开关/adapter 未接入/非自履约/无运单号/非已发货)不产生 pull_log 噪音;
 *     失败(会话装配/缺平台单号/缺平台行号/平台调用异常)必记 pull_log SHIPMENT;
 *     成功回传命令逐字段核对(行级 platformOrderItemId + quantity 翻译,shipTime 时区正确);
 *     事件入口吞异常不影响已提交事务。
 */
class ShipmentSyncServiceTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-09-08T02:00:00Z");
    /** 固定时钟 → 库内时区视角 2026-09-08 10:00 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 10, 0);

    private static final long SHOP_ID = 2L;
    private static final long ORDER_ID = 100L;

    private DeliveryOrderService deliveryOrderService;
    private ShopOrderApi shopOrderApi;
    private ShopService shopService;
    private AdapterRegistry adapterRegistry;
    private PullLogService pullLogService;
    private SysNotificationService notificationService;
    private PlatformClient client;
    private ShipmentSyncService service;

    @BeforeEach
    void setUp() {
        deliveryOrderService = mock(DeliveryOrderService.class);
        shopOrderApi = mock(ShopOrderApi.class);
        shopService = mock(ShopService.class);
        adapterRegistry = mock(AdapterRegistry.class);
        pullLogService = mock(PullLogService.class);
        notificationService = mock(SysNotificationService.class);
        client = mock(PlatformClient.class);
        service = new ShipmentSyncService(deliveryOrderService, shopOrderApi, shopService, adapterRegistry,
                pullLogService, notificationService, Clock.fixed(NOW_INSTANT, PullConsts.ZONE));
        // @Value 开关单测默认 false,显式打开(生产默认 true)
        ReflectionTestUtils.setField(service, "syncEnabled", true);
        when(shopService.getShopSession(SHOP_ID)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
    }

    // ---------- 跳过分支:不回传、不记 pull_log ----------

    @Test
    void syncSkipsWhenDeliveryMissing() {
        when(deliveryOrderService.getById(1L)).thenReturn(null);

        service.sync(1L);

        verifyNoInteractions(shopService, pullLogService);
    }

    @Test
    void syncSkipsWhenAdapterNotRegistered() {
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.empty());
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("SELF_FULFILL", "AMZ-001",
                item(11L, "AMZ-ITEM-1"), item(12L, "AMZ-ITEM-2")));

        service.sync(1L);

        verify(client, never()).uploadTracking(any(), any());
        verifyNoInteractions(pullLogService);
    }

    @Test
    void syncSkipsWhenNotSelfFulfill() {
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("FBA", "AMZ-001",
                item(11L, "AMZ-ITEM-1"), item(12L, "AMZ-ITEM-2")));

        service.sync(1L);

        verify(client, never()).uploadTracking(any(), any());
        verifyNoInteractions(pullLogService);
    }

    @Test
    void syncSkipsWhenTrackingNoBlank() {
        stubDelivery("SHIPPED", "  ", "顺丰");
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("SELF_FULFILL", "AMZ-001",
                item(11L, "AMZ-ITEM-1"), item(12L, "AMZ-ITEM-2")));

        service.sync(1L);

        verify(client, never()).uploadTracking(any(), any());
        verifyNoInteractions(pullLogService);
    }

    @Test
    void syncSkipsWhenDeliveryNotShipped() {
        // 事件只在 ship 成功后发,状态守卫属防御
        stubDelivery("PENDING", "SF1234567890", "顺丰");

        service.sync(1L);

        verify(client, never()).uploadTracking(any(), any());
        verifyNoInteractions(pullLogService);
    }

    // ---------- 成功:命令装配逐字段核对 ----------

    @Test
    void syncUploadsTrackingAndRecordsSuccess() {
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("SELF_FULFILL", "AMZ-001",
                item(11L, "AMZ-ITEM-1"), item(12L, "AMZ-ITEM-2")));

        service.sync(1L);

        ArgumentCaptor<PlatformShipment> captor = ArgumentCaptor.forClass(PlatformShipment.class);
        verify(client).uploadTracking(any(), captor.capture());
        PlatformShipment shipment = captor.getValue();
        assertEquals("AMZ-001", shipment.platformOrderId());
        assertEquals("SF1234567890", shipment.trackingNo());
        // 无 carrierCode 映射表,走 carrierName 兜底(adapter 侧校验"至少其一")
        assertEquals("顺丰", shipment.carrierName());
        // shippedAt 2026-09-08 09:30(Asia/Shanghai)→ 01:30Z
        assertEquals(Instant.parse("2026-09-08T01:30:00Z"), shipment.shipTime());
        assertEquals(2, shipment.items().size());
        assertEquals("AMZ-ITEM-1", shipment.items().get(0).platformOrderItemId());
        assertEquals(6, shipment.items().get(0).quantity());
        assertEquals("AMZ-ITEM-2", shipment.items().get(1).platformOrderItemId());
        assertEquals(4, shipment.items().get(1).quantity());
        verify(pullLogService).recordSuccess(eq(SHOP_ID), eq(PullConsts.DATA_TYPE_SHIPMENT),
                eq(NOW), eq(NOW), eq(1), anyLong(), eq(PullConsts.PULL_WAY_EVENT));
    }

    // ---------- 失败:必记 pull_log ----------

    @Test
    void syncRecordsFailureWhenSessionAssemblyFails() {
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(shopService.getShopSession(SHOP_ID)).thenThrow(new BusinessException("店铺凭证缺失"));

        service.sync(1L);

        verify(client, never()).uploadTracking(any(), any());
        verify(pullLogService).recordFailure(eq(SHOP_ID), eq(PullConsts.DATA_TYPE_SHIPMENT), eq(NOW), eq(NOW),
                eq("BusinessException: 店铺凭证缺失"), anyLong(), eq(PullConsts.PULL_WAY_EVENT));
    }

    @Test
    void syncRecordsFailureWhenOrderMissing() {
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(null);

        service.sync(1L);

        verify(client, never()).uploadTracking(any(), any());
        verify(pullLogService).recordFailure(eq(SHOP_ID), eq(PullConsts.DATA_TYPE_SHIPMENT), eq(NOW), eq(NOW),
                anyString(), anyLong(), eq(PullConsts.PULL_WAY_EVENT));
    }

    @Test
    void syncRecordsFailureWhenPlatformOrderIdMissing() {
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("SELF_FULFILL", "  ",
                item(11L, "AMZ-ITEM-1"), item(12L, "AMZ-ITEM-2")));

        service.sync(1L);

        verify(client, never()).uploadTracking(any(), any());
        verify(pullLogService).recordFailure(eq(SHOP_ID), eq(PullConsts.DATA_TYPE_SHIPMENT), any(), any(),
                anyString(), anyLong(), eq(PullConsts.PULL_WAY_EVENT));
    }

    @Test
    void syncRecordsFailureWhenPlatformItemIdMissing() {
        // 禁静默丢行:半回传比不回传更难对账
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("SELF_FULFILL", "AMZ-001",
                item(11L, "AMZ-ITEM-1"), item(12L, "  ")));

        service.sync(1L);

        verify(client, never()).uploadTracking(any(), any());
        verify(pullLogService).recordFailure(eq(SHOP_ID), eq(PullConsts.DATA_TYPE_SHIPMENT), any(), any(),
                anyString(), anyLong(), eq(PullConsts.PULL_WAY_EVENT));
    }

    @Test
    void syncRecordsFailureAndAlertsWhenUploadThrows() {
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("SELF_FULFILL", "AMZ-001",
                item(11L, "AMZ-ITEM-1"), item(12L, "AMZ-ITEM-2")));
        doThrow(new IllegalStateException("HTTP 429")).when(client).uploadTracking(any(), any());
        when(pullLogService.shouldAlertContinuousFailure(SHOP_ID, PullConsts.DATA_TYPE_SHIPMENT,
                PullConsts.FAILURE_ALERT_THRESHOLD)).thenReturn(true);

        service.sync(1L);

        verify(pullLogService).recordFailure(eq(SHOP_ID), eq(PullConsts.DATA_TYPE_SHIPMENT), eq(NOW), eq(NOW),
                eq("IllegalStateException: HTTP 429"), anyLong(), eq(PullConsts.PULL_WAY_EVENT));
        verify(notificationService).pushAllUsers(eq(SysNotificationService.TYPE_PULL_FAIL), anyString(),
                anyString(), eq(SysNotificationService.BIZ_TYPE_SHOP), eq(SHOP_ID));
    }

    @Test
    void syncDoesNotAlertWhenStreakBelowThreshold() {
        stubDelivery("SHIPPED", "SF1234567890", "顺丰");
        when(shopOrderApi.findDeliveryView(ORDER_ID)).thenReturn(view("SELF_FULFILL", "AMZ-001",
                item(11L, "AMZ-ITEM-1"), item(12L, "AMZ-ITEM-2")));
        doThrow(new IllegalStateException("HTTP 500")).when(client).uploadTracking(any(), any());
        when(pullLogService.shouldAlertContinuousFailure(anyLong(), anyString(), anyInt())).thenReturn(false);

        service.sync(1L);

        verify(pullLogService).recordFailure(anyLong(), anyString(), any(), any(), anyString(), anyLong(), anyString());
        verifyNoInteractions(notificationService);
    }

    // ---------- 事件入口 ----------

    @Test
    void onDeliveryShippedSwallowsExceptionToProtectCommittedTransaction() {
        // 事务已提交,回传失败不得冒泡回 ship 调用方
        when(deliveryOrderService.getById(7L)).thenThrow(new IllegalStateException("boom"));

        assertDoesNotThrow(() -> service.onDeliveryShipped(new DeliveryShippedEvent(7L, ORDER_ID, SHOP_ID)));
    }

    @Test
    void onDeliveryShippedSkippedWhenDisabled() {
        ReflectionTestUtils.setField(service, "syncEnabled", false);

        service.onDeliveryShipped(new DeliveryShippedEvent(7L, ORDER_ID, SHOP_ID));

        verifyNoInteractions(deliveryOrderService, shopService, pullLogService);
    }

    // ---------- 造数 ----------

    private ShopSession session() {
        AuthToken token = new AuthToken();
        token.setAccessToken("at");
        token.setRefreshToken("rt");
        return new ShopSession(SHOP_ID, PlatformType.AMAZON, token);
    }

    private void stubDelivery(String status, String trackingNo, String logisticsCompany) {
        DeliveryOrderResponse delivery = DeliveryOrderResponse.builder()
                .id(1L).deliveryNo("D001").orderId(ORDER_ID).shopId(SHOP_ID)
                .status(status).trackingNo(trackingNo).logisticsCompany(logisticsCompany)
                .shippedAt(LocalDateTime.of(2026, 9, 8, 9, 30))
                .items(List.of(
                        DeliveryOrderItemResponse.builder().id(101L).deliveryId(1L)
                                .orderItemId(11L).skuId(1001L).shipQty(6).build(),
                        DeliveryOrderItemResponse.builder().id(102L).deliveryId(1L)
                                .orderItemId(12L).skuId(1002L).shipQty(4).build()))
                .build();
        when(deliveryOrderService.getById(1L)).thenReturn(delivery);
    }

    private ShopOrderApi.OrderDeliveryView.Item item(long orderItemId, String platformOrderItemId) {
        return ShopOrderApi.OrderDeliveryView.Item.builder()
                .orderItemId(orderItemId).platformOrderItemId(platformOrderItemId)
                .skuId(1000L + orderItemId).quantity(10).build();
    }

    private ShopOrderApi.OrderDeliveryView view(String channel, String platformOrderId,
                                                ShopOrderApi.OrderDeliveryView.Item... items) {
        return ShopOrderApi.OrderDeliveryView.builder()
                .orderId(ORDER_ID).shopId(SHOP_ID).platformOrderId(platformOrderId)
                .orderStatus("WAIT_SHIP").fulfillmentChannel(channel).items(List.of(items))
                .build();
    }
}
