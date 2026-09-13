package com.own.erp.job;

import cn.hutool.core.util.StrUtil;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.graph.AnomalyHighRiskEvent;
import com.own.erp.system.service.SysNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : HIGH 风险订单推送监听(#6 HIGH 推通知,2026-09-12 拍板,设计见
 *         docs/plans/p3-alert-monitor-expansion.md §2.1):消费 AnomalyPersistNode 发布的
 *         AnomalyHighRiskEvent,聚合为一条站内通知经 pushAllUsers 扇出三渠道(禁逐条推防轰炸)。
 *         护栏(每轮经 AiRuntimeProperties 取值,#18 同口径):①静默期 high-quiet-hours 窗口内只推一条;
 *         ②单日上限 high-daily-limit 超限轮次只记日志(0=不限)。MID/LOW 不推(事件源头已过滤);
 *         PII 红线:明细只含订单号/店铺/金额/定级理由,无买家身份信息。
 *         监听在发布线程同步执行,推送失败只记日志不影响工作流主链路
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyHighRiskNotifyListener {

    /** 通知类型:HIGH 风险订单聚合推送(词表登记 sys_notification.notify_type) */
    private static final String NOTIFY_TYPE_HIGH_RISK = "HIGH_RISK_ANOMALY";
    /** 通知标题 */
    private static final String TITLE = "高风险订单预警";

    private final SysNotificationService notificationService;
    private final AiRuntimeProperties runtime;
    private final Clock pullClock;

    @EventListener
    public void onHighRisk(AnomalyHighRiskEvent event) {
        try {
            LocalDateTime now = LocalDateTime.now(pullClock);
            long quietHours = runtime.alertHighQuietHours();
            if (quietHours > 0 && notificationService.existsRecent(NOTIFY_TYPE_HIGH_RISK,
                    now.minusHours(quietHours))) {
                log.info("HIGH 风险订单静默期内已推送,跳过本轮 type={} 条数={}", NOTIFY_TYPE_HIGH_RISK, event.totalCount());
                return;
            }
            int dailyLimit = runtime.alertHighDailyLimit();
            if (dailyLimit > 0 && notificationService.countSince(NOTIFY_TYPE_HIGH_RISK,
                    now.toLocalDate().atStartOfDay()) >= dailyLimit) {
                log.warn("HIGH 风险订单已达单日推送上限 {},本轮 {} 条只记日志不推送", dailyLimit, event.totalCount());
                return;
            }
            int topN = runtime.alertTopN();
            List<String> details = event.items().stream().limit(topN)
                    .map(item -> StrUtil.format("订单 {}(店铺 {}){}", item.orderId(), item.shopId(),
                            StrUtil.nullToEmpty(item.summary())))
                    .toList();
            String suffix = event.totalCount() > details.size() ? " 等" : "";
            String content = StrUtil.format("本轮 HIGH 风险订单 {} 单,请到 AI 建议/订单页人工复核,明细: {}{}",
                    event.totalCount(), String.join("; ", details), suffix);
            int fans = notificationService.pushAllUsers(NOTIFY_TYPE_HIGH_RISK, TITLE, content, null, null);
            log.warn("已推送 HIGH 风险订单通知 条数={} 扇出 {} 人", event.totalCount(), fans);
        } catch (Exception e) {
            // 推送失败只记日志:建议已落库,通知属旁路不回滚工作流
            log.error("HIGH 风险订单通知推送异常", e);
        }
    }
}
