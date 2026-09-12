package com.own.erp.fulfill.response;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA发货单计划行对外结构(SKU 清单,SHIPPED 装箱勾稽基准)
 */
@Builder
public record FbaPlanItemResponse(

        /** 主键 */
        Long id,

        /** FBA发货单ID */
        Long shipmentId,

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** SKU 编码(GoodsQueryApi 契约批量回填) */
        String skuCode,

        /** 计划发货数量(大于 0) */
        Integer planQty
) {
}
