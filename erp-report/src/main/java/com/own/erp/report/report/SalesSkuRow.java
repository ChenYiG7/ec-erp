package com.own.erp.report.report;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 销售SKU明细行(#20 报表域 V1):窗口内逐 SKU 销量合计,销量降序;
 *     名称翻译走 LEFT JOIN product_sku/product——join 不滤已删(#7 拍板:已删 SKU 的历史销量仍要显示名字)
 */
public record SalesSkuRow(

        /** 内部SKU ID */
        Long skuId,

        /** 内部SKU编码 */
        String skuCode,

        /** SPU 商品名称(未绑 SPU 为 NULL) */
        String productName,

        /** 窗口内销量合计(件) */
        long totalQty
) {
}
