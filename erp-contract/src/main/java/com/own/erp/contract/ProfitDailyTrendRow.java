package com.own.erp.contract;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 利润日趋势行(#21 利润看板):同查询条件下按下单日(本地时区 DATE(order_time))聚合,
 *         口径与 OrderProfitSummary 完全一致(装配管线同源复用,非独立 SQL——折算/成本/佣金逐行补齐后再分组);
 *         金额列为该日非空值 Σ(缺汇率/未出库行不计入对应金额,同缺口不静默归零纪律),
 *         profitCny = Σ 行级 profitCny(非空行),与汇总可得性一致
 */
public record ProfitDailyTrendRow(

        /** 统计日(下单日期) */
        LocalDate statDate,

        /** 订单行数 */
        long orderItemCount,

        /** 售价合计(CNY,缺汇率行跳过) */
        BigDecimal salesCny,

        /** 出库成本合计(CNY,未出库行跳过) */
        BigDecimal costCny,

        /** 佣金合计(CNY,待结算行跳过) */
        BigDecimal commissionCny,

        /** 利润合计(CNY,Σ 行级非空 profitCny) */
        BigDecimal profitCny
) {
}
