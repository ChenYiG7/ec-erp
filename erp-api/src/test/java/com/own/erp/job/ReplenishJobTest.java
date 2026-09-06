package com.own.erp.job;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.graph.ReplenishRunResult;
import com.own.erp.ai.graph.ReplenishWorkflow;
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
 * @Description : ReplenishJob 编排单测(#6,AIR:mock 工作流/锁,不依赖数据库):
 *     开关短路、锁被占跳过、正常执行并释放锁、工作流异常不穿透且锁必释放(同 AlertJobTest 口径)
 */
class ReplenishJobTest {

    private ReplenishWorkflow replenishWorkflow;
    private LockService lockService;
    private LockService.Lease lease;
    private ErpAiProperties props;
    private ReplenishJob job;

    @BeforeEach
    void setUp() {
        replenishWorkflow = mock(ReplenishWorkflow.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        when(lockService.tryAcquire("replenish:run")).thenReturn(lease);
        props = new ErpAiProperties();
        job = new ReplenishJob(replenishWorkflow, props, lockService);
    }

    @Test
    void disabledSkipsEverything() {
        props.getReplenish().setEnabled(false);

        job.run();

        verifyNoInteractions(replenishWorkflow, lockService);
    }

    @Test
    void lockUnavailableSkipsRound() {
        when(lockService.tryAcquire("replenish:run")).thenReturn(null);

        job.run();

        verifyNoInteractions(replenishWorkflow);
    }

    @Test
    void runsWorkflowAndReleasesLease() {
        when(replenishWorkflow.run()).thenReturn(ReplenishRunResult.builder()
                .scannedCount(3).suggestedCount(2).persistedCount(2).degraded(false).build());

        job.run();

        verify(replenishWorkflow).run();
        verify(lease).close();
    }

    @Test
    void workflowFailureDoesNotPropagateAndReleasesLease() {
        when(replenishWorkflow.run()).thenThrow(new RuntimeException("llm down"));

        assertDoesNotThrow(job::run);
        verify(lease).close();
    }
}
