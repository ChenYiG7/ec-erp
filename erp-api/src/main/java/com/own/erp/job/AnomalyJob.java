package com.own.erp.job;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.graph.AnomalyRunResult;
import com.own.erp.ai.graph.AnomalyWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 订单异常检测定时调度(#6 两段式,一期跑在 erp-api 进程内,模式同 AlertJob):
 *         - 每日低峰 cron 触发(2026-09-07 拍板,默认 02:30 与补货错峰,共用单线程调度器);
 *           调度编排在 erp-api,工作流在 erp-ai
 *         - 去重语义同日拍板:同订单存在待确认(status=0)建议即跳过(收口 AnomalyScanNode,
 *           在 LLM 评分前省 token);旧建议被采纳/忽略后若订单仍命中允许再产出
 *         - 跨进程防重入 = 全局单锁 anomaly:run(LockService,效率锁语义;去重兜底,双跑也无害)
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 异常工作流全局锁 key(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY = "anomaly:run";

    private final AnomalyWorkflow anomalyWorkflow;
    private final ErpAiProperties props;
    private final LockService lockService;

    /** 每日低峰触发(erp.ai.anomaly.cron 可配,默认 02:30);开关关掉即整体停 */
    @Scheduled(cron = "${erp.ai.anomaly.cron:0 30 2 * * ?}")
    public void run() {
        if (!props.getAnomaly().isEnabled()) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY);
            if (lease == null) {
                log.debug("订单异常检测锁被占(或锁不可用且 fail-closed),跳过本轮");
                return;
            }
            AnomalyRunResult result = anomalyWorkflow.run();
            log.info("订单异常定时任务完成:扫描 {} 单,可疑 {} 单,落库 {} 条,LLM 定级 {} 条,评分降级 {}",
                    result.scannedCount(), result.suspiciousCount(), result.persistedCount(),
                    result.llmScoredCount(), result.degraded());
        } catch (Exception e) {
            // 双保险:工作流内已按段降级,此处兜住未预期异常,不让异常穿透调度线程
            log.error("订单异常定时任务未预期异常", e);
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
