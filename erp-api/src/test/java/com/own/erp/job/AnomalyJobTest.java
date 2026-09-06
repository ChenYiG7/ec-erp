package com.own.erp.job;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.graph.AnomalyRunResult;
import com.own.erp.ai.graph.AnomalyWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AnomalyJob 编排单测(#6,AIR:mock 工作流/锁,不依赖数据库):
 *     开关短路、锁被占跳过、正常执行并释放锁、工作流异常不穿透且锁必释放(同 AlertJobTest 口径)
 */
class AnomalyJobTest {

    private AnomalyWorkflow anomalyWorkflow;
    private LockService lockService;
    private LockService.Lease lease;
    private ErpAiProperties props;
    private AnomalyJob job;

    @BeforeEach
    void setUp() {
        anomalyWorkflow = mock(AnomalyWorkflow.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        when(lockService.tryAcquire("anomaly:run")).thenReturn(lease);
        props = new ErpAiProperties();
        job = new AnomalyJob(anomalyWorkflow, props, lockService);
    }

    @Test
    void disabledSkipsEverything() {
        props.getAnomaly().setEnabled(false);

        job.run();

        verifyNoInteractions(anomalyWorkflow, lockService);
    }

    @Test
    void lockUnavailableSkipsRound() {
        when(lockService.tryAcquire("anomaly:run")).thenReturn(null);

        job.run();

        verifyNoInteractions(anomalyWorkflow);
    }

    @Test
    void runsWorkflowAndReleasesLease() {
        when(anomalyWorkflow.run()).thenReturn(AnomalyRunResult.builder()
                .scannedCount(5).suspiciousCount(1).persistedCount(1).llmScoredCount(1).degraded(false).build());

        job.run();

        verify(anomalyWorkflow).run();
        verify(lease).close();
    }

    @Test
    void workflowFailureDoesNotPropagateAndReleasesLease() {
        when(anomalyWorkflow.run()).thenThrow(new RuntimeException("llm down"));

        assertDoesNotThrow(job::run);
        verify(lease).close();
    }
}
