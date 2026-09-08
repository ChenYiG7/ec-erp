package com.own.erp.job;

import com.own.erp.inventory.service.InventorySnapshotDailyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : InventorySnapshotJob 编排单测(#6 库存快照数据面,AIR:mock 服务/锁,不依赖数据库):
 *     开关短路、锁被占跳过、Clock 派生当日快照、执行并释放锁、异常不穿透且锁必释放
 */
class InventorySnapshotJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);
    private static final Clock CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));

    private InventorySnapshotDailyService snapshotService;
    private LockService lockService;
    private LockService.Lease lease;
    private InventorySnapshotJob job;

    @BeforeEach
    void setUp() {
        snapshotService = mock(InventorySnapshotDailyService.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        when(lockService.tryAcquire("inventory:snapshot")).thenReturn(lease);
        job = new InventorySnapshotJob(snapshotService, lockService, CLOCK);
        ReflectionTestUtils.setField(job, "enabled", true);
    }

    @Test
    void disabledSkipsEverything() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.snapshot();

        verifyNoInteractions(snapshotService, lockService);
    }

    @Test
    void lockUnavailableSkipsRound() {
        when(lockService.tryAcquire("inventory:snapshot")).thenReturn(null);

        job.snapshot();

        verifyNoInteractions(snapshotService);
    }

    @Test
    void snapshotsTodayAndReleasesLease() {
        when(snapshotService.snapshot(TODAY)).thenReturn(15);

        job.snapshot();

        verify(snapshotService).snapshot(TODAY);
        verify(lease).close();
    }

    @Test
    void failureDoesNotPropagateAndReleasesLease() {
        when(snapshotService.snapshot(TODAY)).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(job::snapshot);
        verify(lease).close();
    }
}
