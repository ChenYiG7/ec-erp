package com.own.erp.finance.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : SKU 维度头程费用汇总行(#33 查询面,利润第三层聚合源):
 *     Σ ALLOCATED/CLOSED 头程单 first_leg_alloc,按 SKU 聚合;XML 联表投影
 */
@Builder
public record FirstLegSkuAllocRow(

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** SKU 编码(join product_sku;已删主数据按 docs/07 §6.4 join 不滤已删口径,显名为空时前端回落 ID) */
        String skuCode,

        /** 参与分摊的头程单数 */
        Long shipmentCount,

        /** 头程运费合计(CNY) */
        BigDecimal allocAmountCny

) {
}
