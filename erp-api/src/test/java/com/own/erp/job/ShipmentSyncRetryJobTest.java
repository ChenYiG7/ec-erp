package com.own.erp.job;

import com.own.erp.common.constant.PullConsts;
import com.own.erp.fulfill.service.DeliveryOrderService;
import com.own.erp.shipment.ShipmentSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : ShipmentSyncRetryJob 单测(AIR:mock 协作对象 + 固定时钟,不依赖 DB):
 *     覆盖补偿扫双闸(总开关默认关零噪音 + sync-enabled=false 灰度期跳过)、
 *     候选单逐单重试、单单隔离(sync 未预期异常不中断本轮)、traceId 收尾清理。
 *     筛选/退避/上限语义在 Mapper SQL,Job 层只验开关与编排转发。
 */
class ShipmentSyncRetryJobTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-09-12T02:00:00Z");
    /** 固定时钟 → 库内时区视角 2026-09-12 10:00 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 10, 0);

    private DeliveryOrderService deliveryOrderService;
    private ShipmentSyncService shipmentSyncService;
    private ShipmentSyncRetryJob job;

    @BeforeEach
    void setUp() {
        deliveryOrderService = mock(DeliveryOrderService.class);
        shipmentSyncService = mock(ShipmentSyncService.class);
        job = new ShipmentSyncRetryJob(deliveryOrderService, shipmentSyncService,
                Clock.fixed(NOW_INSTANT, PullConsts.ZONE));
        ReflectionTestUtils.setField(job, "retryEnabled", true);
        ReflectionTestUtils.setField(job, "syncEnabled", true);
        ReflectionTestUtils.setField(job, "retryMaxCount", 5);
        ReflectionTestUtils.setField(job, "retryBackoffMinutes", 10);
        ReflectionTestUtils.setField(job, "retryBatchSize", 100);
    }

    @AfterEach
    void tearDown() {
        MDC.remove("traceId");
    }

    @Test
    void disabledByDefaultSkipsEverythingWithoutNoise() {
        // 总开关默认关(ReflectionTestUtils 未开启):零噪音不触碰任何协作对象
        ReflectionTestUtils.setField(job, "retryEnabled", false);

        job.retryPendingSync();

        verifyNoInteractions(deliveryOrderService, shipmentSyncService);
    }

    @Test
    void skipsWhenSyncMasterSwitchOff() {
        // 双闸:sync-enabled=false(只落本地不回传灰度期)时补偿扫无意义
        ReflectionTestUtils.setField(job, "syncEnabled", false);

        job.retryPendingSync();

        verifyNoInteractions(deliveryOrderService, shipmentSyncService);
    }

    @Test
    void retriesEachCandidateWithScanParameters() {
        when(deliveryOrderService.listSyncRetryCandidateIds(5, 10, NOW, 100)).thenReturn(List.of(1L, 2L));

        job.retryPendingSync();

        // 逐单整体重跑 sync 编排(读单→装配→回传→记 pull_log→回写状态),编排细节归 ShipmentSyncService
        verify(shipmentSyncService).sync(1L);
        verify(shipmentSyncService).sync(2L);
        assertNull(MDC.get("traceId"));
    }

    @Test
    void isolatesUnexpectedFailurePerDelivery() {
        when(deliveryOrderService.listSyncRetryCandidateIds(5, 10, NOW, 100)).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("boom")).when(shipmentSyncService).sync(1L);

        job.retryPendingSync();

        // 单单隔离:首单未预期异常不中断本轮其余单据
        verify(shipmentSyncService).sync(2L);
    }

    @Test
    void noCandidatesIsQuietNoop() {
        when(deliveryOrderService.listSyncRetryCandidateIds(anyInt(), anyInt(), any(), anyInt()))
                .thenReturn(List.of());

        job.retryPendingSync();

        verify(shipmentSyncService, never()).sync(anyLong());
    }
}
