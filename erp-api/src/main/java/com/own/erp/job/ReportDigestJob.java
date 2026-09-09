package com.own.erp.job;

import com.own.erp.report.report.ReportDigest;
import com.own.erp.report.service.ReportDigestService;
import com.own.erp.report.service.ReportDigestService.Period;
import com.own.erp.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : 经营简报定时推送(#23 智能报表 V1,四期 BI 对标 OmniTrade「智能报表」自动化半边,
 *         模式同 RefundReconciliationJob/#19④):
 *         - 三周期三 cron(各带独立键可覆盖):日报每日 08:00(昨日)/周报每周一 08:30(上周)/
 *           月报每月 1 日 09:00(上月);聚合在 ReportDigestService(erp-report,纯读侧),本类只编排:
 *           开关 → 抢锁防重入 → 生成简报 → #14 站内通知扇出(NotifyPushedEvent 出口联动
 *           邮箱/Webhook 渠道,渠道各自开关控制,默认关)
 *         - 主开关 erp.report.digest.enabled 默认 false:经营节奏功能,部署方显式开启
 *           (保护性默认同 Webhook 拍板——避免首次部署每日给全用户发通知的噪声;开启后仍受渠道开关约束)
 *         - 去重拍板:不加 existsRecent 静默期——cron 按日历时刻触发,每周期每次触发的窗口互不重叠,
 *           无"重扫同窗口"问题(勾稽 Job 需要静默期是因其 fixedDelay 每日重扫同一 14 天报告窗);
 *           跨进程防重入 = per-period 全局锁 report:digest:day|week|month(LockService 效率锁,
 *           简报只读,漏发一轮无损失);调度停机错过的 cron 不补发(Spring 调度语义),简报漏发一天无损失
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportDigestJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** per-period 锁 key 前缀(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY_PREFIX = "report:digest:";

    private final ReportDigestService digestService;
    private final SysNotificationService notificationService;
    private final LockService lockService;

    /** 经营简报总开关(默认 false,语义见类注释) */
    @Value("${erp.report.digest.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "${erp.report.digest.daily-cron:0 0 8 * * ?}")
    public void daily() {
        run(Period.DAILY);
    }

    @Scheduled(cron = "${erp.report.digest.weekly-cron:0 30 8 ? * MON}")
    public void weekly() {
        run(Period.WEEKLY);
    }

    @Scheduled(cron = "${erp.report.digest.monthly-cron:0 0 9 1 * ?}")
    public void monthly() {
        run(Period.MONTHLY);
    }

    /** 编排:开关 → 锁 → 生成 → 扇出;通知写失败/生成异常只记日志不上抛调度线程,租约必释放 */
    private void run(Period period) {
        if (!enabled) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY_PREFIX + period.name().toLowerCase());
            if (lease == null) {
                log.debug("经营简报锁被占(或锁不可用且 fail-closed),跳过本轮 period={}", period);
                return;
            }
            ReportDigest digest = digestService.digest(period);
            int fans = notificationService.pushAllUsers(digest.notifyType(), digest.title(),
                    digest.content(), null, null);
            log.info("经营简报已推送 period={} 窗口 {} ~ {} 扇出 {} 人", period, digest.dateFrom(),
                    digest.dateTo(), fans);
        } catch (Exception e) {
            // 双保险:聚合/推送各自收口,此处兜住未预期异常,不让异常穿透调度线程
            log.error("经营简报调度未预期异常 period={}", period, e);
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
