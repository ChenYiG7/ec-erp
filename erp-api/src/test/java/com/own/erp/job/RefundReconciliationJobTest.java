package com.own.erp.job;

import com.own.erp.finance.reconcile.RefundDiffEvent;
import com.own.erp.finance.reconcile.RefundReconciliationAlert;
import com.own.erp.finance.service.RefundReconciliationService;
import com.own.erp.system.service.SysNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : RefundReconciliationJob 编排单测(#19④,AIR:mock 勾稽服务/通知/锁,不依赖数据库):
 *     开关短路、锁被占跳过、无差异不扇出、静默期去重、聚合告警单条扇出(bizType/bizId 留空)、
 *     通知写失败不上抛、异常穿透兜底、锁必释放(模式同 AlertJobTest)
 */
class RefundReconciliationJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
            ZoneId.of("Asia/Shanghai"));

    private RefundReconciliationService reconciliationService;
    private SysNotificationService notificationService;
    private LockService lockService;
    private LockService.Lease lease;
    private RefundReconciliationJob job;

    @BeforeEach
    void setUp() {
        reconciliationService = mock(RefundReconciliationService.class);
        notificationService = mock(SysNotificationService.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        when(lockService.tryAcquire("reconciliation:refund")).thenReturn(lease);
        job = new RefundReconciliationJob(reconciliationService, notificationService, lockService, CLOCK);
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "quietHours", 24L);
    }

    private static RefundReconciliationAlert alert() {
        return RefundReconciliationAlert.builder()
                .notifyType(RefundReconciliationService.NOTIFY_TYPE_REFUND_DIFF)
                .title("退款勾稽差异告警")
                .content("共 1 笔退款勾稽差异:shop=7 订单 111-222 [AMOUNT_MISMATCH] 售后 59.98 USD / 结算 29.99 USD 差额 29.99")
                .diffCount(1)
                .build();
    }

    private static RefundDiffEvent diffEvent() {
        return RefundDiffEvent.builder()
                .diffType(RefundDiffEvent.TYPE_AMOUNT_MISMATCH)
                .shopId(7L).platformOrderId("111-222")
                .aftersaleAmount(new BigDecimal("59.98")).settlementAmount(new BigDecimal("29.99"))
                .aftersaleCurrency("USD").settlementCurrency("USD")
                .diffAmount(new BigDecimal("29.99"))
                .build();
    }

    @Test
    void disabledSkipsEverything() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.reconcile();

        verifyNoInteractions(reconciliationService, notificationService, lockService);
    }

    @Test
    void lockUnavailableSkipsRound() {
        when(lockService.tryAcquire("reconciliation:refund")).thenReturn(null);

        job.reconcile();

        verifyNoInteractions(reconciliationService, notificationService);
    }

    @Test
    void noDiffSkipsPush() {
        when(reconciliationService.reconcileAlert()).thenReturn(null);

        job.reconcile();

        verifyNoInteractions(notificationService);
        verify(lease).close();
    }

    @Test
    void pushesAggregatedAlertWithQuietPeriodDedup() {
        when(reconciliationService.reconcileAlert()).thenReturn(alert());
        when(notificationService.existsRecent(anyString(), any())).thenReturn(false);

        job.reconcile();

        // 聚合事件:每轮至多一条,bizType/bizId 留空(同 #6 聚合口径)
        verify(notificationService).pushAllUsers(eq(RefundReconciliationService.NOTIFY_TYPE_REFUND_DIFF),
                eq("退款勾稽差异告警"), anyString(), isNull(), isNull());
        // 静默期基准时刻 = now - quietHours(24h)
        verify(notificationService).existsRecent(eq(RefundReconciliationService.NOTIFY_TYPE_REFUND_DIFF),
                eq(NOW.minusHours(24)));
        verify(lease).close();
    }

    @Test
    void quietPeriodSkipsPush() {
        when(reconciliationService.reconcileAlert()).thenReturn(alert());
        when(notificationService.existsRecent(anyString(), any())).thenReturn(true);

        job.reconcile();

        verify(notificationService, never()).pushAllUsers(anyString(), anyString(), anyString(), any(), any());
        verify(lease).close();
    }

    @Test
    void pushFailureDoesNotPropagate() {
        when(reconciliationService.reconcileAlert()).thenReturn(alert());
        when(notificationService.existsRecent(anyString(), any())).thenReturn(false);
        doThrow(new RuntimeException("db down")).when(notificationService)
                .pushAllUsers(anyString(), anyString(), anyString(), any(), any());

        job.reconcile();

        // 通知写失败只记日志,不上抛调度线程;租约仍必释放
        verify(lease).close();
    }

    @Test
    void leaseReleasedAfterReconcileThrows() {
        when(reconciliationService.reconcileAlert()).thenThrow(new RuntimeException("boom"));

        job.reconcile();

        // 异常被 Job 兜住不上抛,租约仍必释放
        verify(lease).close();
    }
}
