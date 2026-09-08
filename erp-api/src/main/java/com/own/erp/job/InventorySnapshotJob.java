package com.own.erp.job;

import com.own.erp.inventory.service.InventorySnapshotDailyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 库存日快照调度(#6 库存快照数据面,erp-inventory inventory_snapshot_daily,
 *         一期跑在 erp-api 进程内,模式同 SalesSnapshotJob):
 *         - 每日低峰 cron 触发(默认 01:30,赶在补货工作流 02:00 前出当日数据,销量 01:00 之后错峰)
 *         - 快照当日 inventory 全量四量(在库/占用/在途/可用)按 (SKU, 仓) 落 snapshot;
 *           uk_sku_wh_date 冲突即覆盖(同日重跑/补跑幂等);**不可回溯**——历史日期不会重算,
 *           要回溯需由 inventory_flow 逐日反推(V2 再评估)
 *         - 开关/调度间隔 erp.inventory-snapshot.enabled/cron(yml 可配,默认 01:30 开),
 *           灰度期置 false 只占位不执行;**未接 #18 系统设置面板**——只 yml 配置,
 *           后续若开放面板调参再经 AiRuntimeProperties 出 Optional 口(同 sales)
 *         - 跨进程防重入 = 全局单锁 inventory:snapshot(LockService,效率锁语义;同库同表幂等,双跑也无害)
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventorySnapshotJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 库存快照全局锁 key(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY = "inventory:snapshot";

    private final InventorySnapshotDailyService inventorySnapshotDailyService;
    private final LockService lockService;
    private final Clock pullClock;

    /** 每日低峰触发(erp.inventory-snapshot.cron 可配,默认 01:30);开关关掉即整体停 */
    @Value("${erp.inventory-snapshot.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${erp.inventory-snapshot.cron:0 30 1 * * ?}")
    public void snapshot() {
        if (!enabled) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY);
            if (lease == null) {
                log.debug("库存快照锁被占(或锁不可用且 fail-closed),跳过本轮");
                return;
            }
            LocalDate today = LocalDate.now(pullClock);
            int affected = inventorySnapshotDailyService.snapshot(today);
            log.info("库存日快照定时任务完成:date={} 受影响 {} 行", today, affected);
        } catch (Exception e) {
            // 兜住未预期异常,不让异常穿透调度线程
            log.error("库存日快照定时任务未预期异常", e);
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
