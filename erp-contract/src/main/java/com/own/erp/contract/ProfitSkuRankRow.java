package com.own.erp.contract;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SKU 利润排行行(#21 利润看板):同查询条件下按内部 SKU 聚合(已绑定 SKU 的订单行,
 *         未绑定行无 SKU 维度不参与排行,看板明细页仍可见);排序=利润降序(实现侧钳制 topN ≤100);
 *         口径与 OrderProfitSummary 同源(装配管线复用),毛利率后端统一下发(#21 拍板,前端不再自算)
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
        BigDecimal profitCny,

        /** 毛利率(%,profit/sales×100 保留 1 位小数,HALF_UP;sales≤0 时 NULL 禁猜,#21 拍板改后端下发) */
        BigDecimal grossMarginRate,

        /** 头程运费分摊合计(CNY,#33 第三层 2026-09-12 方案 B:统计窗内 shipped_at 锚点 Σ分摊整窗摊入;无分摊=0) */
        BigDecimal firstLegCny,

        /** 含头程利润(CNY,= profitCny − firstLegCny,#33 第三层净利口径;金额计算后端单点) */
        BigDecimal netProfitCny
) {
}
