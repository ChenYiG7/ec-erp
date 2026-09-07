package com.own.erp.job;

import com.own.erp.config.ErpSalesProperties;
import com.own.erp.order.service.OrderSalesDailyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * @Date : 2026/9/7
 * @Description : SalesSnapshotJob 编排单测(#6 销量数据面,AIR:mock 服务/锁,不依赖数据库):
 *     开关短路、锁被占跳过、窗口日期换算(默认 30 天含今日)、执行并释放锁、异常不穿透且锁必释放
 */
class SalesSnapshotJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);
    private static final Clock CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));

    private OrderSalesDailyService orderSalesDailyService;
    private LockService lockService;
    private LockService.Lease lease;
    private ErpSalesProperties props;
    private com.own.erp.ai.config.AiRuntimeProperties runtime;
    private SalesSnapshotJob job;

    @BeforeEach
    void setUp() {
        orderSalesDailyService = mock(OrderSalesDailyService.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        when(lockService.tryAcquire("sales:snapshot")).thenReturn(lease);
        props = new ErpSalesProperties();
        runtime = mock(com.own.erp.ai.config.AiRuntimeProperties.class);
        when(runtime.salesEnabledOverride()).thenReturn(java.util.Optional.empty());
        when(runtime.salesRebuildDaysOverride()).thenReturn(java.util.Optional.empty());
        job = new SalesSnapshotJob(orderSalesDailyService, props, runtime, lockService, CLOCK);
    }

    @Test
    void disabledSkipsEverything() {
        // DB 覆盖 enabled=false(#18 系统设置运行时开关;Optional 覆盖口优先于 yml 属性)
        when(runtime.salesEnabledOverride()).thenReturn(java.util.Optional.of(false));

        job.snapshot();

        verifyNoInteractions(orderSalesDailyService, lockService);
    }

    @Test
    void lockUnavailableSkipsRound() {
        when(lockService.tryAcquire("sales:snapshot")).thenReturn(null);

        job.snapshot();

        verifyNoInteractions(orderSalesDailyService);
    }

    @Test
    void rebuildsDefaultWindowAndReleasesLease() {
        // 默认 rebuildDays=30:窗口 = [今日-29, 明日),单语句 upsert 由服务侧保证幂等
        when(orderSalesDailyService.rebuildWindow(TODAY.minusDays(29), TODAY.plusDays(1))).thenReturn(12);

        job.snapshot();

        verify(orderSalesDailyService).rebuildWindow(TODAY.minusDays(29), TODAY.plusDays(1));
        verify(lease).close();
    }

    @Test
    void failureDoesNotPropagateAndReleasesLease() {
        when(orderSalesDailyService.rebuildWindow(TODAY.minusDays(29), TODAY.plusDays(1)))
                .thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(job::snapshot);
        verify(lease).close();
    }
}
