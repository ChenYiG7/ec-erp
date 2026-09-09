package com.own.erp.contract;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SKU 利润排行行(#21 利润看板):同查询条件下按内部 SKU 聚合(已绑定 SKU 的订单行,
 *         未绑定行无 SKU 维度不参与排行,看板明细页仍可见);排序=利润降序(实现侧钳制 topN ≤100);
 *         口径与 OrderProfitSummary 同源(装配管线复用),毛利率前端按 profit/sales 自算(缺额口径页面注记)
 */
public record ProfitSkuRankRow(

        /** 内部SKU ID(product_sku.id) */
        Long skuId,

        /** 商品名称快照(同 SKU 多订单行取首见非空) */
        String productName,

        /** 订单行数 */
        long orderItemCount,

        /** 销量合计(件) */
        long quantity,

        /** 售价合计(CNY,缺汇率行跳过) */
        BigDecimal salesCny,

        /** 出库成本合计(CNY,未出库行跳过) */
        BigDecimal costCny,

        /** 佣金合计(CNY,待结算行跳过) */
        BigDecimal commissionCny,

        /** 利润合计(CNY,Σ 行级非空 profitCny) */
        BigDecimal profitCny
) {
}
