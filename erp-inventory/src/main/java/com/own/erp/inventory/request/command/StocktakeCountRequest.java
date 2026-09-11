package com.own.erp.inventory.request.command;

import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点实盘录入入参(COUNTING 阶段逐行录 counted_qty;允许多次调用补录/修正,
 *     后端按入参行覆盖对应明细的实盘数量并即时算 diff_qty=counted-建单快照账面)
 */
@Builder
public record StocktakeCountRequest(

        /** 实盘行(≥1 行;skuId 必须属于本盘点单,countedQty ≥ 0) */
        List<StocktakeCountLine> lines

) {

    /** 单行实盘录入(skuId 定位明细行,countedQty 实盘数量) */
    @Builder
    public record StocktakeCountLine(

            /** SKU ID(product_sku.id) */
            Long skuId,

            /** 实盘数量(≥ 0) */
            Integer countedQty

    ) {
    }
}
