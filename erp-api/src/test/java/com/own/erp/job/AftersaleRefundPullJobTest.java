package com.own.erp.job;

import com.own.erp.aftersale.service.AftersaleOrderService;
import com.own.erp.common.constant.PullConsts;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedRefund;
import com.own.erp.shop.service.PullLogService;
import com.own.erp.shop.service.ShopService;
import com.own.erp.system.service.SysNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : AftersaleRefundPullJob 单测(AIR:mock 协作对象 + 固定时钟,不依赖 Redis/DB):
 *     覆盖总开关默认关(零噪音,#3/#12 拍板)、退款窗口游标(docs/07 §10 必测)、
 *     店铺隔离与锁释放、会话店铺回填。形态同 OrderPullJobTest。
 */
class AftersaleRefundPullJobTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-09-12T02:00:00Z");
    /** 固定时钟 → 库内时区视角 2026-09-12 10:00 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 10, 0);

    private ShopService shopService;
    private AdapterRegistry adapterRegistry;
    private AftersaleOrderService aftersaleOrderService;
    private PullLogService pullLogService;
    private SysNotificationService notificationService;
    private LockService lockService;
    private LockService.Lease lease;
    private PlatformClient client;
    private AftersaleRefundPullJob job;

    @BeforeEach
    void setUp() {
        shopService = mock(ShopService.class);
        adapterRegistry = mock(AdapterRegistry.class);
        aftersaleOrderService = mock(AftersaleOrderService.class);
        pullLogService = mock(PullLogService.class);
        notificationService = mock(SysNotificationService.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        client = mock(PlatformClient.class);
        Clock clock = Clock.fixed(NOW_INSTANT, PullConsts.ZONE);
        when(lockService.tryAcquire(anyString())).thenReturn(lease);

        job = new AftersaleRefundPullJob(shopService, adapterRegistry, aftersaleOrderService,
                pullLogService, notificationService, lockService, clock);
        ReflectionTestUtils.setField(job, "firstPullDays", 90);
    }

    @Test
    void disabledByDefaultSkipsEverythingWithoutNoise() {
        // 总开关默认关(ReflectionTestUtils 未开启):零噪音不触碰任何协作对象(#3/#12 拍板)
        job.pullRefunds();

        verifyNoInteractions(shopService, adapterRegistry, aftersaleOrderService, pullLogService, lockService);
    }

    @Test
    void pullsWithOverlapWindowFromLastSuccessCursor() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        LocalDateTime cursor = NOW.minusHours(1);
        when(pullLogService.findLastSuccessWindowEnd(1L, PullConsts.DATA_TYPE_REFUND)).thenReturn(cursor);
        when(client.pullRefunds(any(), any(), any())).thenReturn(List.of());

        job.pullRefunds();

        // 窗口 = 上次成功 window_end 左叠 5 分钟(docs/04 拉单策略,同订单拉单口径)
        Instant expectedStart = cursor.minusMinutes(PullConsts.WINDOW_OVERLAP_MINUTES).atZone(PullConsts.ZONE).toInstant();
        Instant expectedEnd = NOW.atZone(PullConsts.ZONE).toInstant();
        verify(client).pullRefunds(any(ShopSession.class), eq(expectedStart), eq(expectedEnd));
        verify(pullLogService).recordSuccess(eq(1L), eq(PullConsts.DATA_TYPE_REFUND),
                eq(cursor.minusMinutes(PullConsts.WINDOW_OVERLAP_MINUTES)), eq(NOW), eq(0), anyLong(),
                eq(PullConsts.PULL_WAY_JOB));
        verify(lease).close();
    }

    @Test
    void firstPullGoesBackConfiguredDays() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(1L, PullConsts.DATA_TYPE_REFUND)).thenReturn(null);
        when(client.pullRefunds(any(), any(), any())).thenReturn(List.of());

        job.pullRefunds();

        Instant expectedStart = NOW.minusDays(90).atZone(PullConsts.ZONE).toInstant();
        verify(client).pullRefunds(any(ShopSession.class), eq(expectedStart), eq(NOW.atZone(PullConsts.ZONE).toInstant()));
    }

    @Test
    void savesRefundsWithSessionShopIdBackfilled() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(1L, PullConsts.DATA_TYPE_REFUND)).thenReturn(NOW.minusHours(1));

        UnifiedRefund refund = UnifiedRefund.builder().platformRefundId("R-1").build();
        when(client.pullRefunds(any(), any(), any())).thenReturn(List.of(refund));

        job.pullRefunds();

        // 以会话店铺回填 shopId(防报文内脏值),逐条走 saveUnifiedRefund 唯一入口
        verify(aftersaleOrderService).saveUnifiedRefund(refund);
        verify(pullLogService).recordSuccess(eq(1L), eq(PullConsts.DATA_TYPE_REFUND), any(), any(),
                eq(1), anyLong(), eq(PullConsts.PULL_WAY_JOB));
    }

    @Test
    void recordsFailureWhenSessionAssemblyFails() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenThrow(new IllegalArgumentException("尚未配置授权凭证"));

        job.pullRefunds();

        verify(pullLogService).recordFailure(eq(1L), eq(PullConsts.DATA_TYPE_REFUND), any(), any(),
                contains("尚未配置授权凭证"), eq(0L), eq(PullConsts.PULL_WAY_JOB));
        verifyNoInteractions(adapterRegistry);
        verify(lockService, never()).tryAcquire(anyString());
    }

    @Test
    void shopFailureIsolatedAndCursorNotAdvanced() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L, 2L));
        when(shopService.getShopSession(anyLong())).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(anyLong(), anyString())).thenReturn(NOW.minusHours(1));
        when(client.pullRefunds(any(), any(), any()))
                .thenThrow(new RuntimeException("platform 500"))
                .thenReturn(List.of());

        job.pullRefunds();

        // 店铺1失败:记失败日志不推游标;店铺2照常成功(失败隔离)
        verify(pullLogService).recordFailure(eq(1L), eq(PullConsts.DATA_TYPE_REFUND), any(), any(),
                anyString(), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        verify(pullLogService, never()).recordSuccess(eq(1L), any(), any(), any(), anyInt(), anyLong(), anyString());
        verify(pullLogService).recordSuccess(eq(2L), eq(PullConsts.DATA_TYPE_REFUND), any(), any(),
                eq(0), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        // 两店租约均释放(异常路径 finally close 兜底)
        verify(lease, times(2)).close();
    }

    @Test
    void pushesInSiteAlertWhenFailureStreakHitsThreshold() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(session());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(pullLogService.findLastSuccessWindowEnd(anyLong(), anyString())).thenReturn(NOW.minusHours(1));
        when(client.pullRefunds(any(), any(), any())).thenThrow(new RuntimeException("platform 500"));
        when(pullLogService.shouldAlertContinuousFailure(1L, PullConsts.DATA_TYPE_REFUND,
                PullConsts.FAILURE_ALERT_THRESHOLD)).thenReturn(true);

        job.pullRefunds();

        verify(notificationService).pushAllUsers(eq(SysNotificationService.TYPE_PULL_FAIL),
                eq("售后退款拉取连续失败告警"), contains("连续失败 3 次"),
                eq(SysNotificationService.BIZ_TYPE_SHOP), eq(1L));
        verify(lease).close();
    }

    private ShopSession session() {
        return new ShopSession(1L, PlatformType.AMAZON, new AuthToken());
    }
}
