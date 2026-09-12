package com.own.erp.job;

import com.own.erp.fulfill.constant.FbaConsts;
import com.own.erp.fulfill.entity.FbaShipment;
import com.own.erp.fulfill.service.FbaShipmentService;
import com.own.erp.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 收货对账提醒调度(docs/plans/fba-shipment.md,V1 骨架):每日扫 SHIPPED/RECEIVING
 *         超期未关闭的发货单,聚合一告警经 #14 站内通知扇出(同 RefundReconciliationJob 编排模式)。
 *         - 默认关(erp.fulfill.fba-reconciliation.enabled=false):V1 收货登记全人工,数据量起来后再开,
 *           避免无消费噪音;自动取数(SP-API getShipments 报告)随 V2 TODO(#35,#3 真凭证)
 *         - 静默期去重按 notifyType 全局判(默认 24h),sys_notification 即"上次告警时间"存储(同 #6/#19④ 口径)
 *         - 跨进程防重入 = 全局单锁 reconciliation:fba-receive(LockService 效率锁语义;只读扫描,漏扫一轮无损失)
 *         - @Scheduled 线程不经 TraceIdFilter(#9),入口自行 MDC.put traceId、finally 强制清
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FbaReconciliationJob {

    private static final String MDC_TRACE_ID = "traceId";
    private static final int TRACE_ID_LENGTH = 16;
    /** 扫描全局锁 key(LockService 内再拼 erp:lock: 命名空间,docs/07 §1 禁魔法值) */
    private static final String LOCK_KEY = "reconciliation:fba-receive";
    /** 告警类型(静默期去重键;同 RefundReconciliationAlert.notifyType 形态) */
    private static final String NOTIFY_TYPE = "FBA_RECEIVE_OVERDUE";

    private final FbaShipmentService fbaShipmentService;
    private final SysNotificationService notificationService;
    private final LockService lockService;
    private final Clock pullClock;

    /** 默认关:V1 人工登记,无消费噪音;验收后按需开启 */
    @Value("${erp.fulfill.fba-reconciliation.enabled:false}")
    private boolean enabled;

    /** 静默期(小时):同类型告警全局去重窗口,同 #6 预警静默期拍板 */
    @Value("${erp.fulfill.fba-reconciliation.quiet-hours:24}")
    private long quietHours;

    /** 超期阈值(天):SHIPPED/RECEIVING 超过 N 天未关闭即提醒(默认 14 天,平台 FBA 收货常规 3~10 天) */
    @Value("${erp.fulfill.fba-reconciliation.overdue-days:14}")
    private int overdueDays;

    @Scheduled(fixedDelayString = "${erp.fulfill.fba-reconciliation.interval-ms:86400000}")
    public void scan() {
        if (!enabled) {
            return;
        }
        MDC.put(MDC_TRACE_ID, newTraceId());
        LockService.Lease lease = null;
        try {
            lease = lockService.tryAcquire(LOCK_KEY);
            if (lease == null) {
                log.debug("FBA收货对账锁被占(或锁不可用且 fail-closed),跳过本轮");
                return;
            }
            LocalDateTime deadline = LocalDateTime.now(pullClock).minusDays(overdueDays);
            List<FbaShipment> overdue = fbaShipmentService.listOverdueForReceive(deadline);
            if (overdue.isEmpty()) {
                log.info("FBA收货对账扫描完成,无超期未关闭单据");
                return;
            }
            pushQuietly(overdue);
        } catch (Exception e) {
            // 双保险:扫描/推送已各自收口,此处兜住未预期异常,不让异常穿透调度线程
            log.error("FBA收货对账调度未预期异常", e);
        } finally {
            if (lease != null) {
                lease.close();
            }
            MDC.remove(MDC_TRACE_ID);
        }
    }

    /** 推送:静默期内已发同类型告警则跳过;通知写失败只记日志(聚合事件 bizType/bizId 留空,同 #6 口径) */
    private void pushQuietly(List<FbaShipment> overdue) {
        try {
            LocalDateTime since = LocalDateTime.now(pullClock).minusHours(quietHours);
            if (notificationService.existsRecent(NOTIFY_TYPE, since)) {
                log.info("静默期内已发同类型告警,跳过 type={}", NOTIFY_TYPE);
                return;
            }
            String content = "以下 FBA 发货单发出已超 " + overdueDays + " 天未完成收货对账(共 "
                    + overdue.size() + " 单):"
                    + overdue.stream()
                    .limit(10)
                    .map(s -> s.getShipmentNo() + "(" + s.getStatus() + ")")
                    .reduce((a, b) -> a + "、" + b).orElse("")
                    + (overdue.size() > 10 ? " 等" : "")
                    + ",请及时登记平台收货数量并关闭(来源:FbaReconciliationJob)";
            int fans = notificationService.pushAllUsers(NOTIFY_TYPE,
                    "FBA发货单收货对账超期提醒", content, FbaConsts.BIZ_TYPE_FBA_SHIPMENT, null);
            log.warn("已推送FBA收货对账超期提醒 超期 {} 单,扇出 {} 人", overdue.size(), fans);
        } catch (Exception e) {
            log.error("FBA收货对账告警推送异常 type={}", NOTIFY_TYPE, e);
        }
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
