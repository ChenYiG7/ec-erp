package com.own.erp.job;

import com.own.erp.finance.service.ProfitPeriodReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 周期利润兜底重算调度(#19 三口径第二层,计划书 §2.1):PARSED 结算报告同事务派生周期行之外,
 *         本 Job 每日低频全量重算兜底(幂等靠 uk(shop_id,settlement_id) ODKU upsert,不漏不重;
 *         单期失败 Service 内 try-catch 隔离不回滚全批)。
 *         模式同 RefundReconciliationJob:开关 → 全局锁(纯聚合只读,漏扫一轮无损失)→ 重算;
 *         跨进程防重入 LockService.tryAcquire("finance:profit-period")。
 *         <p><b>默认开 erp.finance.profit-period.enabled=true(#32 校差算法 2026-09-12 落地 + PARSED
 *         同事务钩子接线后翻开;无 PARSED 报告时空转零噪音)。@Scheduled 线程不经 TraceIdFilter(#9),入口 MDC.put、finally 清。</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfitPeriodJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 全局锁 key(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY = "finance:profit-period";

    private final ProfitPeriodReportService profitPeriodReportService;
    private final LockService lockService;

    /** 兜底重算开关:#32 校差落地后默认开,无 PARSED 报告空转零噪音 */
    @Value("${erp.finance.profit-period.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${erp.finance.profit-period.interval-ms:86400000}")
    public void rebuildPeriods() {
        if (!enabled) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY);
            if (lease == null) {
                log.debug("周期利润重算锁被占(或锁不可用且 fail-closed),跳过本轮");
                return;
            }
            profitPeriodReportService.rebuildAllParsedReports();
            log.info("周期利润兜底重算完成");
        } catch (Exception e) {
            log.error("周期利润重算调度未预期异常", e);
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
