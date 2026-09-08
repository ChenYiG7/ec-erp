package com.own.erp.finance.reconcile;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 退款勾稽聚合告警(#19④):RefundReconciliationService.reconcileAlert 产出,
 *     erp-api RefundReconciliationJob 消费后经 SysNotificationService.pushAllUsers(#14 唯一写入口)
 *     扇出——erp-finance 不依赖 erp-system,模块间只传本 record(铁律 2,同 erp-ai AlertEvent 先例)。
 *     聚合口径:每轮扫描至多一条(差异明细列 topN),bizType/bizId 留空;静默期去重按 notifyType 在 Job 侧收口
 */
@Builder
public record RefundReconciliationAlert(

        /** 通知类型(REFUND_DIFF,常量收口 RefundReconciliationService) */
        String notifyType,

        /** 通知标题 */
        String title,

        /** 通知内容(差异订单明细;写侧统一截断 1000) */
        String content,

        /** 差异订单笔数(内容可能因 topN 截断,笔数以本字段为准) */
        int diffCount
) {
}
