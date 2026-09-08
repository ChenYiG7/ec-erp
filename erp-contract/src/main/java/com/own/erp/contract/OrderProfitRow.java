package com.own.erp.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 实时销售利润行(#19③,订单行粒度):利润 = 售价(CNY) − 出库成本(CNY) − 平台佣金(CNY)。
 *         折算口径拍板(docs/03 §6.1):售价×下单日回溯汇率(resolveRate,禁取表内最新,CNY 短路=1,
 *         缺报价 rate/salesCny 为 NULL 禁猜);成本=OUT_SHIP 移动加权快照按订单行聚合,未出库 NULL;
 *         佣金=settlement_detail COMMISSION 归集,无结算数据 NULL(禁费率猜算)。
 *         profitCny:成本缺失 → NULL;仅佣金缺失 → 售价−成本(毛利,commissionMissing 标志),
 *         三者齐 → 售价−成本−佣金
 */
public record OrderProfitRow(

        /** 订单行ID(shop_order_item.id) */
        Long orderItemId,

        /** 订单ID(shop_order.id) */
        Long orderId,

        /** 平台订单号 */
        String platformOrderId,

        /** 平台(PlatformType 枚举名) */
        String platform,

        /** 下单时间(汇率回溯锚点) */
        LocalDateTime orderTime,

        /** 店铺ID */
        Long shopId,

        /** 平台订单行号(佣金归集键,可空=平台未给行号) */
        String platformOrderItemId,

        /** 平台SKU快照 */
        String platformSku,

        /** 商品名称快照 */
        String productName,

        /** 内部SKU(可空=未绑定) */
        Long skuId,

        /** 数量 */
        Integer quantity,

        /** 小计金额(原币)=单价×数量 */
        BigDecimal itemAmount,

        /** 币种(ISO 4217) */
        String currency,

        /** 下单日回溯汇率(缺报价 NULL) */
        BigDecimal rate,

        /** 售价(CNY)=itemAmount×rate;rate NULL → NULL */
        BigDecimal salesCny,

        /** 出库成本(CNY,移动加权);未出库 NULL */
        BigDecimal costCny,

        /** 平台佣金(CNY);无结算数据 NULL */
        BigDecimal commissionCny,

        /** 利润(CNY);成本缺失 NULL,佣金缺失=售价−成本 */
        BigDecimal profitCny,

        /** true=未出库(无成本快照) */
        boolean costMissing,

        /** true=待结算(无佣金归集) */
        boolean commissionMissing
) {
}
