package com.own.erp.finance.response;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 期间资金汇总 SQL 投影行(#31,MyBatis record 构造映射,列别名对齐组件名,同 RefundSideRow 先例);
 *     Service 装配期间参数后输出 PaymentSummaryResponse
 */
public record PaymentSummaryRow(

        /** 期间回款合计(CNY,缺汇率行不计入) */
        BigDecimal incomeCny,

        /** 期间付款合计(CNY,缺汇率行不计入) */
        BigDecimal expenseCny,

        /** 期间流水笔数(含缺汇率行) */
        Long totalCount,

        /** 缺汇率行数(缺口计数不静默) */
        Long missingRateCount
) {
}
