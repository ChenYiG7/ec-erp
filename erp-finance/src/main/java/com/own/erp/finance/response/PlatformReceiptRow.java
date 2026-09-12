package com.own.erp.finance.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 平台回款视图行(#31 资金流追踪查询面②):按店铺聚合期间 INCOME 回款
 *     (结算回款派生流水 + 面向平台的手工登记流水);跨币种时原币金额只计数不汇总,
 *     CNY 列按落库冻结汇率折算,缺汇率行进 missingRateCount(缺口纪律,不静默归零)
 */
@Builder
public record PlatformReceiptRow(

        /** 店铺ID(shop.id) */
        Long shopId,

        /** 店铺名称(join shop 装配) */
        String shopName,

        /** 回款笔数 */
        Long receiptCount,

        /** 回款原币金额(同店铺多币种时仅混合参考,精确口径看 CNY 列与流水明细) */
        BigDecimal amount,

        /** 回款折合 CNY(缺汇率行不计入) */
        BigDecimal amountCny,

        /** 缺汇率行数(amount_cny IS NULL,前端显"折算缺失") */
        Long missingRateCount
) {
}
