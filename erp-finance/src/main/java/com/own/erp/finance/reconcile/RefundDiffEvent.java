package com.own.erp.finance.reconcile;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 退款勾稽差异事件(#19④):RefundReconciliationService 比对产出的差异载体,
 *     每差异订单一条;Job 侧聚合成一条站内告警(#14 通道,#6 AlertEvent 聚合扇出同款纪律)
 */
@Builder
public record RefundDiffEvent(

        /** 差异类型(词表见 TYPE_* 常量) */
        String diffType,

        /** 店铺ID */
        Long shopId,

        /** 平台订单号 */
        String platformOrderId,

        /** 售后侧退款合计(正数,原币;币种混算不比金额时为 null) */
        BigDecimal aftersaleAmount,

        /** 结算侧退款合计(取正,原币;结算侧无 REFUND 行时为 null) */
        BigDecimal settlementAmount,

        /** 售后侧币种(单币种时单值;混币时逗号并列,供人工判读) */
        String aftersaleCurrency,

        /** 结算侧币种(单币种时单值;混币时逗号并列;结算侧无行时为 null) */
        String settlementCurrency,

        /** 差额 = 售后 − 结算(正=售后侧多退;币种混算/结算缺失为 null,禁跨口径计算) */
        BigDecimal diffAmount
) {

    /** 差异类型:金额不符(双侧有行,差额超容差) */
    public static final String TYPE_AMOUNT_MISMATCH = "AMOUNT_MISMATCH";
    /** 差异类型:结算侧缺失(售后已退款,该订单在 PARSED 结算报告中无任何 REFUND 行) */
    public static final String TYPE_MISSING_IN_SETTLEMENT = "MISSING_IN_SETTLEMENT";
    /** 差异类型:币种不一致(任一侧同订单多币种,或两侧币种不同——金额比对失真,禁混币计算) */
    public static final String TYPE_CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
}
