package com.own.erp.report.report;

import java.time.LocalDate;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 销售周报聚合行(#20 报表域 V1):按自然周(周一为一周起点,MySQL WEEKDAY 口径)聚合,
 *     weekStart=该周一日期;数据源与日报同表(order_sales_daily),分组粒度差异
 */
public record SalesWeeklyRow(

        /** 周起点(周一) */
        LocalDate weekStart,

        /** 本周销量合计(件) */
        long totalQty,

        /** 本周有销量的 SKU 数 */
        long skuCount
) {
}
