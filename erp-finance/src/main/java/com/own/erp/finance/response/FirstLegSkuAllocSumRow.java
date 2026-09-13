package com.own.erp.finance.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : SKU 维度头程分摊合计投影行(#33 第二查询面,利润第三层 enrichment 批量取数:
 *     ProfitQueryService.listSkuProfitRank 按排名 SKU 集合批量取周期内 Σ分摊,方案 B 整窗摊入)。
 *     普通 class(MyBatis setter 映射,同 FirstLegSkuAllocRow 口径)
 */
@Data
public class FirstLegSkuAllocSumRow {

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 周期内头程运费分摊合计(CNY) */
    private BigDecimal allocAmountCny;
}
