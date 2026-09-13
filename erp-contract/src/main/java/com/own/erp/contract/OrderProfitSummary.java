package com.own.erp.contract;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 实时销售利润汇总(#19③):同查询条件下全量订单行聚合(与行口径一致,可得行求和);
 *         三个 missing 计数暴露数据缺口——缺汇率/未出库/待结算行不计入对应金额但单独计数,
 *         禁把缺口静默归零(V1 费用事实禁静默截断同 #19② 勾稽纪律)
 */
public record OrderProfitSummary(

        /** 订单行数(统计口径内全部行) */
        long orderItemCount,

        /** 售价合计(CNY,缺汇率行跳过) */
        BigDecimal salesCny,

        /** 出库成本合计(CNY,未出库行跳过) */
        BigDecimal costCny,

        /** 佣金合计(CNY,实际结算佣金优先,缺口行按 platform_fee_rate 估算计入;无实际也无估算跳过) */
        BigDecimal commissionCny,

        /** 利润合计(CNY,Σ行级 profitCny——与行口径一致:缺成本/缺汇率行不计) */
        BigDecimal profitCny,

        /** 缺汇率行数(rate NULL,禁猜汇率不折算) */
        long missingRateCount,

        /** 未出库行数(无成本快照) */
        long costMissingCount,

        /** 缺实际佣金行数(无结算归集;费率估算命中的行仍计本数,2026-09-11 #19 预估模型) */
        long commissionMissingCount,

        /** 毛利率(%,profit/sales×100 保留 1 位小数,HALF_UP;sales≤0 时 NULL 禁猜,#21 拍板改后端下发) */
        BigDecimal grossMarginRate
) {
}
