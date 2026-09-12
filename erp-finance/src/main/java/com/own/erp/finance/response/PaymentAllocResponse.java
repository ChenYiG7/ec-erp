package com.own.erp.finance.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水分摊行对外结构(#31):采购付款→采购单的分摊明细,详情态带出
 */
@Builder
public record PaymentAllocResponse(

        /** 分摊行ID(payment_alloc.id) */
        Long id,

        /** 采购单ID(purchase_order.id) */
        Long poId,

        /** 采购单号(经 PurchaseQueryApi 契约回填,跨域禁横向依赖) */
        String poNo,

        /** 分摊金额(CNY,采购付款强制本位币) */
        BigDecimal amount
) {
}
