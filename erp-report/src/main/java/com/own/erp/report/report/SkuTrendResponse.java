package com.own.erp.report.report;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : 商品分析 SKU 趋势响应(#22):SKU 翻译 + 逐日趋势 + 窗口汇总 三件一体;
 *     skuId 无翻译行(脏 id/已不存在)时 sku 为 null,趋势/汇总照常返回(页面回落显示裸 ID)
 */
public record SkuTrendResponse(SkuOptionRow sku, List<SkuTrendRow> trend, SkuTrendSummary summary) {
}
