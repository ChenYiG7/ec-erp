package com.own.erp.job;

import com.own.erp.ai.alert.AlertEngine;

import com.own.erp.ai.alert.AlertEvent;
import com.own.erp.ai.config.ErpAlertProperties;
import com.own.erp.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 库存预警扫描调度(#6,一期跑在 erp-api 进程内,模式同 OrderPullJob):
 *         - 每小时一扫(fixedDelay,上一轮结束再计时);规则执行在 AlertEngine(erp-ai),本类只编排:
 *           开关 → 抢锁防重入 → 评估 → 静默期去重 → #14 站内通知扇出
 *         - 静默期去重按 notifyType 全局判(erp.alert.quiet-hours,默认 24h),sys_notification
 *           即"上次告警时间"存储,免建去重表;每条通知独立 try/catch,写失败只记日志不阻断其余事件
 *         - 跨进程防重入 = 全局单锁 alert:scan(LockService,效率锁语义;扫描本身只读,漏扫一轮无损失)
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 预警扫描全局锁 key(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY = "alert:scan";

    private final AlertEngine alertEngine;
    private final SysNotificationService notificationService;
    private final LockService lockService;
    private final ErpAlertProperties props;
    private final Clock pullClock;

    @Scheduled(fixedDelayString = "${erp.alert.interval-ms:3600000}")
    public void scan() {
        if (!props.isEnabled()) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY);
            if (lease == null) {
                log.debug("预警扫描锁被占(或锁不可用且 fail-closed),跳过本轮");
                return;
            }
            List<AlertEvent> events = alertEngine.evaluate();
            if (events.isEmpty()) {
                log.info("库存预警扫描完成,无命中");
                return;
            }
            LocalDateTime since = LocalDateTime.now(pullClock).minusHours(props.getQuietHours());
            log.info("库存预警扫描命中 {} 条,静默期判定基准={}", events.size(), since);
            for (AlertEvent event : events) {
                pushQuietly(event, since);
            }
        } catch (Exception e) {
            // 双保险:引擎已按规则隔离,此处兜住未预期异常,不让异常穿透调度线程
            log.error("库存预警扫描未预期异常", e);
        } finally {
            if (lease != null) {
                lease.close();
            }
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /** 单事件推送:静默期内已发同类型告警则跳过;通知写失败只记日志,不中断其余事件(#14 同款纪律) */
    private void pushQuietly(AlertEvent event, LocalDateTime since) {
        try {
            if (notificationService.existsRecent(event.notifyType(), since)) {
                log.info("静默期内已发同类型告警,跳过 type={}", event.notifyType());
                return;
            }
            int fans = notificationService.pushAllUsers(event.notifyType(), event.title(),
                    event.content(), event.bizType(), event.bizId());
            log.warn("已推送预警通知 type={} 扇出 {} 人", event.notifyType(), fans);
        } catch (Exception e) {
            log.error("预警通知推送异常 type={}", event.notifyType(), e);
        }
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
