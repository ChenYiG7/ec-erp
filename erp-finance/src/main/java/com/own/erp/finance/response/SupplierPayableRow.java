package com.own.erp.finance.response;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 供应商往来视图行(#31 资金流追踪查询面②):应付/已付/待付按供应商聚合。
 *     应付 = Σ 非 DRAFT 采购单 total_amount(DRAFT 未审核不构成负债);
 *     已付 = Σ payment_alloc 关联 NORMAL 流水(按采购单归属供应商);待付 = 应付 − 已付;
 *     未分摊的预付/挂账款不进本视图(无采购单归属,V1 查流水明细核对)
 */
@Builder
public record SupplierPayableRow(

        /** 供应商ID(supplier.id) */
        Long supplierId,

        /** 供应商名称 */
        String supplierName,

        /** 账期天数(supplier.settle_days,V1 仅展示) */
        Integer settleDays,

        /** 应付金额(Σ 非草稿采购单总额,CNY) */
        BigDecimal payableAmount,

        /** 已付金额(Σ NORMAL 采购付款分摊,CNY) */
        BigDecimal paidAmount,

        /** 待付金额(应付 − 已付,CNY) */
        BigDecimal unpaidAmount
) {
}
