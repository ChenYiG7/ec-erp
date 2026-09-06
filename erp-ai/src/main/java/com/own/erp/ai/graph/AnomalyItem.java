package com.own.erp.ai.graph;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 可疑订单聚合行(#6 订单异常检测两段式):scan 节点产出(score 前为规则定级),
 *     score 节点回填 LLM 风险/理由(llmScored=true),persist 节点按行落 ai_suggestion。
 *     同单多规则命中合并一行(hitRules 列表,基线风险取 max);只带 OrderView 契约字段,无 PII
 */
@Builder(toBuilder = true)
public record AnomalyItem(

        /** 订单ID(shop_order.id,落库 refId) */
        Long orderId,

        /** 店铺ID(shop.id,落库 shop_id) */
        Long shopId,

        /** 命中规则列表(同单多规则并存) */
        List<AnomalyRule> hitRules,

        /** 规则基线风险(命中规则取 max;LLM 定级失败回落此值) */
        String baselineRisk,

        /** 币种(ISO 4217) */
        String currency,

        /** 订单总金额(原币) */
        BigDecimal orderAmount,

        /** 下单日汇率快照(原币→本位币,null 按 1,#4 落库口径) */
        BigDecimal exchangeRate,

        /** 优惠金额(原币) */
        BigDecimal discountAmount,

        /** 下单时间(平台侧) */
        LocalDateTime orderTime,

        /** 支付时间(金额类规则非空守卫) */
        LocalDateTime paidTime,

        /** 摘要(LLM 理由或规则模板,落库 summary 列) */
        String summary,

        /** 是否已送 LLM 评分(true=LLM 定级;false=规则定级/降级/超限未送评) */
        boolean llmScored
) {
}
