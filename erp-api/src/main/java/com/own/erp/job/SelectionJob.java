package com.own.erp.job;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.graph.SelectionRunResult;
import com.own.erp.ai.graph.SelectionWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 智能选品定时调度(#6 三工作流定时接线,2026-09-12 拍板,模式同 ReplenishJob):
 *         - 每日低峰 cron 触发(默认 04:00,与补货/异常/采购/文案错峰,共用单线程调度器);
 *           调度编排在 erp-api,工作流在 erp-ai;建议落 ai_suggestion 待人工确认,红线不变
 *         - 去重语义:同 SKU 存在待确认(status=0)选品建议即跳过(工作流内收口)
 *         - 跨进程防重入 = 全局单锁 selection:run(LockService,效率锁语义;去重兜底,双跑也无害)
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectionJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 智能选品工作流全局锁 key(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY = "selection:run";

    private final SelectionWorkflow selectionWorkflow;
    private final ErpAiProperties props;
    private final LockService lockService;

    /** 每日低峰触发(erp.ai.selection.cron 可配,默认 04:00);开关关掉即整体停 */
    @Scheduled(cron = "${erp.ai.selection.cron:0 0 4 * * ?}")
    public void run() {
        if (!props.getSelection().isEnabled()) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY);
            if (lease == null) {
                log.debug("智能选品锁被占(或锁不可用且 fail-closed),跳过本轮");
                return;
            }
            SelectionRunResult result = selectionWorkflow.run();
            log.info("智能选品定时任务完成:扫描 {} 行,候选 {} SKU,入选 {} 个,落库 {} 条,摘要降级 {}",
                    result.scannedCount(), result.candidateCount(), result.selectedCount(),
                    result.persistedCount(), result.degraded());
        } catch (Exception e) {
            // 双保险:工作流内已按段降级,此处兜住未预期异常,不让异常穿透调度线程
            log.error("智能选品定时任务未预期异常", e);
        } finally {
            if (lease != null) {
                lease.close();
            }
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
