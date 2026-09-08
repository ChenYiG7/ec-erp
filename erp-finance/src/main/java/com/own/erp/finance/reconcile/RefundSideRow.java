package com.own.erp.finance.reconcile;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 退款勾稽侧投影(#19④,#12 遗留收口):售后侧/结算侧聚合查询共用,
 *     两侧同键(店铺+平台订单号)同向(正数)后由 RefundReconciliationService 比对。
 *     MyBatis record 构造映射,列别名与组件名对齐(同 OrderProfitAmountGroup 先例)
 */
public record RefundSideRow(

        /** 店铺ID */
        Long shopId,

        /** 平台订单号(Amazon OrderId 原文;两侧唯一公共归集键) */
        String platformOrderId,

        /** 币种:售后单 currency / 结算报告 currency(参与比对前先过币种一致性判定) */
        String currency,

        /** 退款金额合计(恒正数口径):售后侧=Σ refund_amount;结算侧=Σ(−amount),REFUND 行报告原值为负 */
        BigDecimal amount
) {
}
