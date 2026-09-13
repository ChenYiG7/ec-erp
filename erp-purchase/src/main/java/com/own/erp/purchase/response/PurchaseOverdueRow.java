package com.own.erp.purchase.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 超期未付清采购单查询投影行(#31 账期到期提醒取数,Mapper XML 出参,
 *     经 PurchaseOrderService 转 erp-contract PurchaseQueryApi.PurchaseOverdueView 契约视图):
 *     audit_time + settle_days < asOf 且未付清的采购单快照。普通 class(MyBatis setter 映射,
 *     record 需构造器映射不引入,同 SkuSupplierRow 口径)
 */
@Data
public class PurchaseOverdueRow {

    /** 采购单ID(purchase_order.id) */
    private Long poId;

    /** 采购单号 */
    private String poNo;

    /** 供应商ID(supplier.id) */
    private Long supplierId;

    /** 供应商名称(supplier.name) */
    private String supplierName;

    /** 账期天数(supplier.settle_days,审核日起算) */
    private Integer settleDays;

    /** 审核时间(账期起算锚点) */
    private LocalDateTime auditTime;

    /** 未付金额(本位币,ΣNORMAL 分摊聚合后待付) */
    private BigDecimal unpaidAmount;
}
