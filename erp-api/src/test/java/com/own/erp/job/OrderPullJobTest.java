package com.own.erp.job;

import com.own.erp.common.constant.PullConsts;
import com.own.erp.order.service.ShopOrderService;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedOrder;
import com.own.erp.shop.service.PullLogService;
import com.own.erp.shop.service.ShopProductSkuService;
import com.own.erp.shop.service.ShopService;
import com.own.erp.system.service.SysNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : OrderPullJob 单测(AIR:mock 协作对象 + 固定时钟,不依赖 Redis/DB),
 *     覆盖拉单窗口/游标计算(docs/07 §10 必测)、店铺隔离与锁释放。
 *     店铺锁为 Redisson LockService(#13 锁选型):"锁被占" = tryAcquire 打桩返回 null;
 *     释放断言 = verify(lease).close()
 */
class OrderPullJobTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-09-04T02:00:00Z");
    /** 固定时钟 → 库内时区视角 2026-09-04 10:00 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 10, 0);

    private ShopService shopService;
    private AdapterRegistry adapterRegistry;
    private ShopOrderService shopOrderService;
    private ShopProductSkuService shopProductSkuService;
    private PullLogService pullLogService;
    private SysNotificationService notificationService;
    private LockService lockService;
    private LockService.Lease lease;
    private PlatformClient client;
    private OrderPullJob job;

    @BeforeEach
    void setUp() {
        shopService = mock(ShopService.class);
        adapterRegistry = mock(AdapterRegistry.class);
        shopOrderService = mock(ShopOrderService.class);
        shopProductSkuService = mock(ShopProductSkuService.class);
        pullLogService = mock(PullLogService.class);
        notificationService = mock(SysNotificationService.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        client = mock(PlatformClient.class);
        Clock clock = Clock.fixed(NOW_INSTANT, PullConsts.ZONE);
        when(lockService.tryAcquire(anyString())).thenReturn(lease);

        job = new OrderPullJob(shopService, adapterRegistry, shopOrderService, shopProductSkuService,
                pullLogService, notificationService, lockService, clock);
        ReflectionTestUtils.setField(job, "firstPullDays", 90);
    }

    @Test
    void skipsShopWhenPlatformAdapterMissing() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.empty());

        job.pullOrders();

        verify(client, never()).pullOrders(any(), any(), any());
        verifyNoInteractions(pullLogService);
        // 未走到抢锁段:不应触碰锁
        verify(lockService, never()).tryAcquire(anyString());
    }

    /**
     * #3:会话装配失败(凭证缺失/解密失败/Token 刷新失败)从"仅跳过"升级为记 pull_log——
     * docs/04「刷新失败告警并停该店铺拉单」,连续 3 次走告警判定;不触碰 adapter/锁,不拉取
     */
    @Test
    void recordsFailureWhenSessionAssemblyFails() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenThrow(new IllegalArgumentException("尚未配置授权凭证"));

        job.pullOrders();

        verify(pullLogService).recordFailure(eq(1L), eq(PullConsts.DATA_TYPE_ORDER), any(), any(),
                contains("尚未配置授权凭证"), eq(0L), eq(PullConsts.PULL_WAY_JOB));
        verifyNoInteractions(adapterRegistry);
        verify(lockService, never()).tryAcquire(anyString());
        verify(client, never()).pullOrders(any(), any(), any());
    }

    /** 他方持锁 = tryAcquire 返回 null(Redisson 语义),调度应跳过该店 */
    @Test
    void skipsShopWhenLockHeldByOtherWorker() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(lockService.tryAcquire(anyString())).thenReturn(null);

        job.pullOrders();

        verify(client, never()).pullOrders(any(), any(), any());
        verifyNoInteractions(pullLogService);
        verify(lease, never()).close();
    }

    @Test
    void pullsWithOverlapWindowFromLastSuccessCursor() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        LocalDateTime cursor = NOW.minusHours(1);
        when(pullLogService.findLastSuccessWindowEnd(1L, PullConsts.DATA_TYPE_ORDER)).thenReturn(cursor);
        when(client.pullOrders(any(), any(), any())).thenReturn(List.of());

        job.pullOrders();

        // 窗口 = 上次成功 window_end 左叠 5 分钟(docs/04 拉单策略)
        Instant expectedStart = cursor.minusMinutes(PullConsts.WINDOW_OVERLAP_MINUTES).atZone(PullConsts.ZONE).toInstant();
        Instant expectedEnd = NOW.atZone(PullConsts.ZONE).toInstant();
        verify(client).pullOrders(any(ShopSession.class), eq(expectedStart), eq(expectedEnd));
        verify(pullLogService).recordSuccess(eq(1L), eq(PullConsts.DATA_TYPE_ORDER),
                eq(cursor.minusMinutes(PullConsts.WINDOW_OVERLAP_MINUTES)), eq(NOW), eq(0), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        verify(lease).close();
    }

    @Test
    void firstPullGoesBackConfiguredDays() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(1L, PullConsts.DATA_TYPE_ORDER)).thenReturn(null);
        when(client.pullOrders(any(), any(), any())).thenReturn(List.of());

        job.pullOrders();

        Instant expectedStart = NOW.minusDays(90).atZone(PullConsts.ZONE).toInstant();
        verify(client).pullOrders(any(ShopSession.class), eq(expectedStart), eq(NOW.atZone(PullConsts.ZONE).toInstant()));
    }

    @Test
    void savesOrdersWithSkuMappingAndSessionShopId() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(1L, PullConsts.DATA_TYPE_ORDER)).thenReturn(NOW.minusHours(1));

        UnifiedOrder order = new UnifiedOrder();
        order.setPlatformOrderId("P-001");
        UnifiedOrder.Item item = new UnifiedOrder.Item();
        item.setSellerSku("SKU-A");
        order.setItems(List.of(item));
        when(client.pullOrders(any(), any(), any())).thenReturn(List.of(order));
        when(shopProductSkuService.mapSellerSkuToSkuId(eq(1L), eq(Set.of("SKU-A")))).thenReturn(Map.of("SKU-A", 99L));

        job.pullOrders();

        // 以会话店铺回填 shopId,并把批量映射传给落库
        assertEquals(1L, order.getShopId());
        verify(shopOrderService).saveUnifiedOrder(1L, order, Map.of("SKU-A", 99L));
        verify(pullLogService).recordSuccess(eq(1L), eq(PullConsts.DATA_TYPE_ORDER), any(), any(),
                eq(1), anyLong(), eq(PullConsts.PULL_WAY_JOB));
    }

    @Test
    void shopFailureIsolatedAndCursorNotAdvanced() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L, 2L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(shopService.getShopSession(2L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(anyLong(), anyString())).thenReturn(NOW.minusHours(1));
        when(client.pullOrders(any(), any(), any()))
                .thenThrow(new RuntimeException("platform 500"))
                .thenReturn(List.of());

        job.pullOrders();

        // 店铺1失败:记失败日志不推游标;店铺2照常成功(失败隔离)
        verify(pullLogService).recordFailure(eq(1L), eq(PullConsts.DATA_TYPE_ORDER), any(), any(),
                anyString(), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        verify(pullLogService, never()).recordSuccess(eq(1L), any(), any(), any(), anyInt(), anyLong(), anyString());
        verify(pullLogService).recordSuccess(eq(2L), eq(PullConsts.DATA_TYPE_ORDER), any(), any(),
                eq(0), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        // 两店租约均释放(异常路径 finally close 兜底)
        verify(lease, times(2)).close();
        // 判定为"未达告警条件"(mock 默认 false)时不推告警
        verify(notificationService, never()).pushAllUsers(any(), any(), any(), any(), any());
    }

    @Test
    void pushesInSiteAlertWhenFailureStreakHitsThreshold() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(anyLong(), anyString())).thenReturn(NOW.minusHours(1));
        when(client.pullOrders(any(), any(), any())).thenThrow(new RuntimeException("platform 500"));
        when(pullLogService.shouldAlertContinuousFailure(1L, PullConsts.DATA_TYPE_ORDER,
                PullConsts.FAILURE_ALERT_THRESHOLD)).thenReturn(true);

        job.pullOrders();

        verify(notificationService).pushAllUsers(eq(SysNotificationService.TYPE_PULL_FAIL),
                eq("订单拉取连续失败告警"), contains("连续失败 3 次"),
                eq(SysNotificationService.BIZ_TYPE_SHOP), eq(1L));
        // 告警路径同样要释放租约
        verify(lease).close();
    }

    @Test
    void alertWriteFailureDoesNotBreakOtherShops() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L, 2L));
        when(shopService.getShopSession(anyLong())).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(anyLong(), anyString())).thenReturn(NOW.minusHours(1));
        when(client.pullOrders(any(), any(), any()))
                .thenThrow(new RuntimeException("platform 500"))
                .thenReturn(List.of());
        when(pullLogService.shouldAlertContinuousFailure(eq(1L), anyString(), anyInt())).thenReturn(true);
        doThrow(new IllegalStateException("通知库写入失败")).when(notificationService)
                .pushAllUsers(anyString(), anyString(), anyString(), anyString(), anyLong());

        job.pullOrders();

        // 告警写失败只吞异常记日志:店铺1已记失败日志,店铺2照常成功
        verify(notificationService).pushAllUsers(anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(pullLogService).recordFailure(eq(1L), eq(PullConsts.DATA_TYPE_ORDER), any(), any(),
                anyString(), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        verify(pullLogService).recordSuccess(eq(2L), eq(PullConsts.DATA_TYPE_ORDER), any(), any(),
                eq(0), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        verify(lease, times(2)).close();
    }

    private ShopSession session() {
        return new ShopSession(1L, PlatformType.AMAZON, new AuthToken());
    }
}
