package com.own.erp.job;

import com.own.erp.ai.alert.AlertEngine;
import com.own.erp.ai.alert.AlertEvent;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.system.service.SysNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
 * @Date : 2026/9/6
 * @Description : AlertJob 编排单测(#6,AIR:mock 引擎/通知/锁,不依赖数据库):
 *     开关短路、锁被占跳过、静默期去重、逐事件推送、单事件失败不阻断、锁必释放
 */
class AlertJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
            ZoneId.of("Asia/Shanghai"));

    private AlertEngine alertEngine;
    private SysNotificationService notificationService;
    private LockService lockService;
    private LockService.Lease lease;
    private AiRuntimeProperties runtime;
    private AlertJob job;

    @BeforeEach
    void setUp() {
        alertEngine = mock(AlertEngine.class);
        notificationService = mock(SysNotificationService.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        when(lockService.tryAcquire("alert:scan")).thenReturn(lease);
        runtime = mock(AiRuntimeProperties.class);
        when(runtime.alertEnabled()).thenReturn(true);
        when(runtime.alertQuietHours()).thenReturn(24L);
        job = new AlertJob(alertEngine, notificationService, lockService, runtime, CLOCK);
    }

    private AlertEvent event(String notifyType) {
        return AlertEvent.builder().notifyType(notifyType).title("t").content("c").build();
    }

    @Test
    void disabledSkipsEverything() {
        // DB 覆盖 enabled=false(#18 系统设置运行时开关,替代原 yml 属性注入口)
        when(runtime.alertEnabled()).thenReturn(false);

        job.scan();

        verifyNoInteractions(alertEngine, notificationService, lockService);
    }

    @Test
    void lockUnavailableSkipsRound() {
        when(lockService.tryAcquire("alert:scan")).thenReturn(null);

        job.scan();

        verifyNoInteractions(alertEngine, notificationService);
    }

    @Test
    void noHitsSkipsPush() {
        when(alertEngine.evaluate()).thenReturn(List.of());

        job.scan();

        verifyNoInteractions(notificationService);
        verify(lease).close();
    }

    @Test
    void pushesEachEventWithQuietPeriodDedup() {
        when(alertEngine.evaluate()).thenReturn(List.of(
                event(AlertEvent.TYPE_LOW_STOCK), event(AlertEvent.TYPE_SHIP_TIMEOUT)));
        when(notificationService.existsRecent(eq(AlertEvent.TYPE_LOW_STOCK), any())).thenReturn(true);
        when(notificationService.existsRecent(eq(AlertEvent.TYPE_SHIP_TIMEOUT), any())).thenReturn(false);

        job.scan();

        verify(notificationService, never()).pushAllUsers(eq(AlertEvent.TYPE_LOW_STOCK), anyString(),
                anyString(), any(), any());
        verify(notificationService).pushAllUsers(eq(AlertEvent.TYPE_SHIP_TIMEOUT), eq("t"),
                eq("c"), any(), any());
        // 通知基准时刻 = now - quietHours(24h)
        verify(notificationService).existsRecent(eq(AlertEvent.TYPE_SHIP_TIMEOUT),
                eq(NOW.minusHours(24)));
        verify(lease).close();
    }

    @Test
    void pushFailureDoesNotBlockOtherEvents() {
        when(alertEngine.evaluate()).thenReturn(List.of(
                event(AlertEvent.TYPE_LOW_STOCK), event(AlertEvent.TYPE_SHIP_TIMEOUT)));
        when(notificationService.existsRecent(anyString(), any())).thenReturn(false);
        doThrow(new RuntimeException("db down")).when(notificationService)
                .pushAllUsers(eq(AlertEvent.TYPE_LOW_STOCK), anyString(), anyString(), any(), any());

        job.scan();

        verify(notificationService).pushAllUsers(eq(AlertEvent.TYPE_SHIP_TIMEOUT), anyString(),
                anyString(), any(), any());
        verify(lease).close();
    }

    @Test
    void leaseReleasedAfterEvaluationThrows() {
        when(alertEngine.evaluate()).thenThrow(new RuntimeException("boom"));

        job.scan();

        // 异常被 Job 兜住不上抛,租约仍必释放
        verify(lease).close();
    }
}
