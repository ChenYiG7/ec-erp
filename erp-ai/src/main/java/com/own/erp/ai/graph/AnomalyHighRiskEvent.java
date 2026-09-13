package com.own.erp.ai.graph;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : HIGH 风险订单事件(#6 HIGH 推通知,2026-09-12 拍板):AnomalyPersistNode 落库完成后发布,
 *     erp-api 监听器消费(erp-ai 禁依赖 erp-system,铁律 2——事件介体解耦,同 NotifyPushedEvent 先例)。
 *     聚合语义:每轮工作流至多一条(禁逐条推防轰炸);PII 红线——只携带订单号/店铺/金额/理由,
 *     禁买家身份信息(计划书 docs/plans/p3-alert-monitor-expansion.md §2.1)
 */
public record AnomalyHighRiskEvent(

        /** 本轮 HIGH 风险订单数 */
        int totalCount,

        /** 明细快照(聚合通知取 topN 用) */
        List<HighRiskItem> items
) {

    /** HIGH 明细项:仅订单号/店铺/金额/定级理由,无买家 PII */
    public record HighRiskItem(
            Long orderId,
            Long shopId,
            String summary,
            String orderAmount,
            String currency
    ) {
    }
}
