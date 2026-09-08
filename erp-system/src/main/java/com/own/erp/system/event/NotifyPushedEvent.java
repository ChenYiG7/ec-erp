package com.own.erp.system.event;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 站内通知已扇出事件(#14 Webhook 通知渠道 V1):SysNotificationService.pushAllUsers 发布,
 *         WebhookPushService 以 AFTER_COMMIT 相位消费外推群机器人——**事务提交后才外推**
 *         (同 #11 发货回传拍板),外推失败只记日志不回滚站内通知。
 *         事件发布方与监听方同在 erp-system,不进 erp-common(DeliveryShippedEvent/SystemConfigChangedEvent
 *         进 common 是因为跨模块,此处无跨模块依赖,避免 common 词表膨胀)。
 *         只带 notifyType/title/content(群报文素材),bizType/bizId 是站内跳转语义群消息用不上不透传
 */
public record NotifyPushedEvent(

        /** 通知类型(PULL_FAIL/REFUND_DIFF 等) */
        String notifyType,

        /** 通知标题(写侧已截断 ≤1000) */
        String title,

        /** 通知内容(可空) */
        String content
) {
}
