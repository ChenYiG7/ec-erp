package com.own.erp.job;

import com.own.erp.finance.reconcile.RefundReconciliationAlert;
import com.own.erp.finance.service.RefundReconciliationService;
import com.own.erp.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 退款勾稽调度(#19④,#12 遗留"退款金额与财务勾稽"收口,模式同 AlertJob/#6):
 *         - 每日一扫(fixedDelay,上一轮结束再计时;结算报告 14 天一份,频率不支持高频);
 *           比对在 RefundReconciliationService(erp-finance,纯读侧),本类只编排:
 *           开关 → 抢锁防重入 → 勾稽 → 静默期去重 → #14 站内通知扇出
 *         - 静默期去重按 notifyType 全局判(默认 24h),sys_notification 即"上次告警时间"存储
 *           (同 #6 口径);告警每轮聚合一条(bizType/bizId 留空,差异明细列 topN),
 *           通知写失败只记日志不阻断主流程(#14 同款纪律)
 *         - 跨进程防重入 = 全局单锁 reconciliation:refund(LockService 效率锁语义;勾稽只读,漏扫一轮无损失)
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundReconciliationJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 勾稽扫描全局锁 key(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY = "reconciliation:refund";

    private final RefundReconciliationService reconciliationService;
    private final SysNotificationService notificationService;
    private final LockService lockService;
    private final Clock pullClock;

    /** 勾稽开关:数据面未就绪(结算报告未拉/售后未同步)可整体关闭降噪 */
    @Value("${erp.finance.refund-reconciliation.enabled:true}")
    private boolean enabled;

    /** 静默期(小时):同类型告警全局去重窗口,同 #6 预警静默期拍板 */
    @Value("${erp.finance.refund-reconciliation.quiet-hours:24}")
    private long quietHours;

    @Scheduled(fixedDelayString = "${erp.finance.refund-reconciliation.interval-ms:86400000}")
    public void reconcile() {
        if (!enabled) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY);
            if (lease == null) {
                log.debug("退款勾稽锁被占(或锁不可用且 fail-closed),跳过本轮");
                return;
            }
            RefundReconciliationAlert alert = reconciliationService.reconcileAlert();
            if (alert == null) {
                log.info("退款勾稽完成,无差异");
                return;
            }
            pushQuietly(alert);
        } catch (Exception e) {
            // 双保险:比对/推送已各自收口,此处兜住未预期异常,不让异常穿透调度线程
            log.error("退款勾稽调度未预期异常", e);
        } finally {
            if (lease != null) {
                lease.close();
            }
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /** 推送:静默期内已发同类型告警则跳过;通知写失败只记日志(聚合事件 bizType/bizId 留空,同 #6 口径) */
    private void pushQuietly(RefundReconciliationAlert alert) {
        try {
            LocalDateTime since = LocalDateTime.now(pullClock).minusHours(quietHours);
            if (notificationService.existsRecent(alert.notifyType(), since)) {
                log.info("静默期内已发同类型告警,跳过 type={}", alert.notifyType());
                return;
            }
            int fans = notificationService.pushAllUsers(alert.notifyType(), alert.title(),
                    alert.content(), null, null);
            log.warn("已推送退款勾稽差异告警 差异 {} 笔,扇出 {} 人", alert.diffCount(), fans);
        } catch (Exception e) {
            log.error("退款勾稽告警推送异常 type={}", alert.notifyType(), e);
        }
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
