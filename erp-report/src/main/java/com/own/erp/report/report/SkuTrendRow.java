package com.own.erp.report.report;

import java.time.LocalDate;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : 商品分析 SKU 日趋势行(#22):日期轴由 Service 按窗口逐日生成对齐——销量缺日=0(无销售),
 *     库存缺日=null(快照缺失不猜,#19 禁猜口径,前端断点不画)。SQL 侧两条简单 GROUP BY 各产半行
 *     (销量语句只填 qtySold/库存语句只填 qtyOnHand,另一字段 null 由 Service 合并),
 *     日期轴不进 SQL——MySQL 5.7 无递归 CTE,SQL 兼容性红线(docs/07)
 */
public record SkuTrendRow(LocalDate statDate, Integer qtySold, Integer qtyOnHand) {
}
