package com.own.erp.finance.profit;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 利润聚合投影(#19③):成本聚合(按内部订单行)/佣金聚合(按店铺+平台订单行)共用,
 *     两查询未命中的键不在结果里,由 ProfitQueryService 按 null 语义装配(未出库/待结算)
 */
public record OrderProfitAmountGroup(

        /** 店铺ID(佣金聚合用;成本聚合恒 null) */
        Long shopId,

        /** 内部订单行ID(成本聚合键) */
        Long orderItemId,

        /** 平台订单行号(佣金聚合键) */
        String platformOrderItemId,

        /** 聚合金额:成本=Σ(−cost_amount)正数 CNY;佣金=Σ amount 原币 */
        BigDecimal amount
) {
}
