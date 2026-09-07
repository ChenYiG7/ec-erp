package com.own.erp.job;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.config.ErpSalesProperties;
import com.own.erp.order.service.OrderSalesDailyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 销量日统计调度(#6 销量数据面,一期跑在 erp-api 进程内,模式同 AlertJob):
 *         - 每日低峰 cron 触发(默认 01:00,赶在补货工作流 02:00 前出当日数据,错峰共用单线程调度器)
 *         - 每轮窗口重算近 N 天(erp.sales.rebuild-days 默认 30):单语句原子 upsert,
 *           uk_sku_date 幂等可重试,覆盖订单状态回传/取消单修正(30 天外的取消单不再回刷,V1 已知边界);
 *           #18 后 enabled/rebuild-days 每轮经 AiRuntimeProperties 取 DB 覆盖值,无覆盖回落 yml 默认
 *           (ErpSalesProperties 在 erp-api,AiRuntimeProperties 不反向依赖,覆盖值出 Optional 口)
 *         - 跨进程防重入 = 全局单锁 sales:snapshot(LockService,效率锁语义;重算幂等,双跑也无害)
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesSnapshotJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 销量重算全局锁 key(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY = "sales:snapshot";

    private final OrderSalesDailyService orderSalesDailyService;
    private final ErpSalesProperties props;
    private final AiRuntimeProperties runtime;
    private final LockService lockService;
    private final Clock pullClock;

    /** 每日低峰触发(erp.sales.cron 可配,默认 01:00);开关关掉即整体停 */
    @Scheduled(cron = "${erp.sales.cron:0 0 1 * * ?}")
    public void snapshot() {
        // 开关/窗口每轮取值(#18 系统设置):DB 覆盖值优先,yml 默认兜底,保存即时生效
        if (!runtime.salesEnabledOverride().orElse(props.isEnabled())) {
            return;
        }
        int rebuildDays = runtime.salesRebuildDaysOverride().orElse(props.getRebuildDays());
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY);
            if (lease == null) {
                log.debug("销量重算锁被占(或锁不可用且 fail-closed),跳过本轮");
                return;
            }
            LocalDate today = LocalDate.now(pullClock);
            LocalDate startDate = today.minusDays(Math.max(rebuildDays, 1) - 1L);
            int affected = orderSalesDailyService.rebuildWindow(startDate, today.plusDays(1));
            log.info("销量日统计定时任务完成:窗口 [{}, {}) 受影响 {} 行", startDate, today.plusDays(1), affected);
        } catch (Exception e) {
            // 兜住未预期异常,不让异常穿透调度线程
            log.error("销量日统计定时任务未预期异常", e);
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
