package com.own.erp.report.report;

/**
 * @author : chenyi
 * @Date : 2026/9/9
 * @Description : 商品分析 SKU 选项行(#22 四期 BI 首个功能):销量日表∪库存快照表出现过的 SKU,
 *     join 商品翻译不滤已删(#7 拍板——已删主数据的历史数据仍可读)
 */
public record SkuOptionRow(Long skuId, String skuCode, String productName) {
}
