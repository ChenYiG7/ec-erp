package com.own.erp.fulfill.response;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA箱内件对外结构(发出量源:跨箱同SKU Σquantity)
 */
@Builder
public record FbaBoxItemResponse(

        /** 主键 */
        Long id,

        /** 箱ID */
        Long boxId,

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** SKU 编码(GoodsQueryApi 契约批量回填) */
        String skuCode,

        /** 箱内件数(大于 0) */
        Integer quantity
) {
}
