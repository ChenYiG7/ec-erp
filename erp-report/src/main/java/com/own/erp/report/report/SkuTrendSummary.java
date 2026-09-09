package com.own.erp.report.report;

import java.time.LocalDate;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : 商品分析窗口汇总(#22):由趋势行 Service 内存计算(≤366 行,不单出 SQL)——
 *     销量合计/动销天数(qtySold&gt;0 的天数)/期末库存(窗口内最新非空快照)。
 *     窗口内无任何快照行时 latestQtyOnHand/latestStockDate 双 null(快照面缺失如实呈现,不猜)
 */
public record SkuTrendSummary(long totalQtySold, int activeDays, Integer latestQtyOnHand, LocalDate latestStockDate) {
}
