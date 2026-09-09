package com.own.erp.report.report;

import java.time.LocalDate;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 销售日报聚合行(#20 报表域 V1):order_sales_daily 按统计日聚合(支付日口径,
 *     已支付态三态,未绑定 SKU 不统计——销量面既有口径,docs/03 §7.2)
 */
public record SalesDailyRow(

        /** 统计日期 */
        LocalDate statDate,

        /** 当日销量合计(件) */
        long totalQty,

        /** 当日有销量的 SKU 数 */
        long skuCount
) {
}
