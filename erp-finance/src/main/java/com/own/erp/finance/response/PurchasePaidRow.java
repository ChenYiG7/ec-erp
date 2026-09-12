package com.own.erp.finance.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 采购单已付聚合行(#31 采购单资金视图):采购列表/详情按 Σ NORMAL 流水分摊取数。
 *     待付 = 采购总额 − 已付在 SQL 侧 DECIMAL 计算(前端金额 string 红线,禁 JS 浮点);
 *     无任何分摊的单不被聚合 SQL 返回,Service 补零行时总额/待付为 null(调用方按 paid=0 自洽)
 */
@Builder
public record PurchasePaidRow(

        /** 采购单ID(purchase_order.id) */
        Long poId,

        /** 采购总金额(CNY,冗余自采购单头供待付列直显) */
        BigDecimal totalAmount,

        /** 已付金额(Σ payment_alloc 关联 NORMAL 流水;无分摊为 0) */
        BigDecimal paidAmount,

        /** 待付金额(采购总额 − 已付,SQL DECIMAL 计算) */
        BigDecimal unpaidAmount
) {
}
