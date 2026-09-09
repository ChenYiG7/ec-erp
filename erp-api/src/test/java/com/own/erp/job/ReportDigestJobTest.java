package com.own.erp.job;

import com.own.erp.report.report.ReportDigest;
import com.own.erp.report.service.ReportDigestService;
import com.own.erp.report.service.ReportDigestService.Period;
import com.own.erp.system.service.SysNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : ReportDigestJob 编排单测(#23,AIR:mock 简报服务/通知/锁,不依赖数据库):
 *     开关短路、锁被占跳过、简报扇出(bizType/bizId 留空,同 #6/#19 聚合口径)、per-period 独立锁、
 *     生成/推送异常不上抛、租约必释放(模式同 RefundReconciliationJobTest)
 */
class ReportDigestJobTest {

    private ReportDigestService digestService;
    private SysNotificationService notificationService;
    private LockService lockService;
    private LockService.Lease lease;
    private ReportDigestJob job;

    @BeforeEach
    void setUp() {
        digestService = mock(ReportDigestService.class);
        notificationService = mock(SysNotificationService.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        when(lockService.tryAcquire("report:digest:daily")).thenReturn(lease);
        when(lockService.tryAcquire("report:digest:weekly")).thenReturn(lease);
        when(lockService.tryAcquire("report:digest:monthly")).thenReturn(lease);
        job = new ReportDigestJob(digestService, notificationService, lockService);
        ReflectionTestUtils.setField(job, "enabled", true);
    }

    private static ReportDigest digest(String notifyType) {
        return new ReportDigest("DAILY", notifyType, "经营日报 2026-09-08",
                "统计窗口:2026-09-08 ~ 2026-09-08\n销量合计:30 件", LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 8));
    }

    @Test
    void disabledSkipsEverything() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.daily();

        verifyNoInteractions(digestService, notificationService, lockService);
    }

    @Test
    void lockOccupiedSkipsRound() {
        when(lockService.tryAcquire("report:digest:daily")).thenReturn(null);

        job.daily();

        verifyNoInteractions(digestService, notificationService);
    }

    @Test
    void pushesDigestWithEmptyBizRef() {
        when(digestService.digest(Period.DAILY)).thenReturn(digest("REPORT_DAILY"));

        job.daily();

        // 聚合简报:每轮至多一条,bizType/bizId 留空(同 #6/#19 聚合口径)
        verify(notificationService).pushAllUsers(eq("REPORT_DAILY"), eq("经营日报 2026-09-08"),
                anyString(), isNull(), isNull());
        verify(lease).close();
    }

    @Test
    void weeklyAndMonthlyUseOwnLockKeys() {
        when(digestService.digest(Period.WEEKLY)).thenReturn(digest("REPORT_WEEKLY"));
        when(digestService.digest(Period.MONTHLY)).thenReturn(digest("REPORT_MONTHLY"));

        job.weekly();
        job.monthly();

        verify(lockService).tryAcquire("report:digest:weekly");
        verify(lockService).tryAcquire("report:digest:monthly");
        verify(notificationService).pushAllUsers(eq("REPORT_WEEKLY"), anyString(), anyString(), isNull(), isNull());
        verify(notificationService).pushAllUsers(eq("REPORT_MONTHLY"), anyString(), anyString(), isNull(), isNull());
        verify(lease, times(2)).close();
    }

    @Test
    void digestFailureDoesNotPropagate() {
        when(digestService.digest(Period.DAILY)).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> job.daily());

        verify(notificationService, never()).pushAllUsers(anyString(), anyString(), anyString(), any(), any());
        verify(lease).close();
    }

    @Test
    void pushFailureDoesNotPropagate() {
        when(digestService.digest(Period.DAILY)).thenReturn(digest("REPORT_DAILY"));
        doThrow(new RuntimeException("db down")).when(notificationService)
                .pushAllUsers(anyString(), anyString(), anyString(), any(), any());

        assertDoesNotThrow(() -> job.daily());

        // 通知写失败只记日志不上抛调度线程;租约仍必释放
        verify(lease).close();
    }
}
