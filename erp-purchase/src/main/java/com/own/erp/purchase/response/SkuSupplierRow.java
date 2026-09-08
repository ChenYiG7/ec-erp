package com.own.erp.purchase.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SKU→最新供应商映射查询投影行(#17 采购建议取数,Mapper XML 窗口函数出参,
 *     经 PurchaseOrderService 转 erp-contract PurchaseQueryApi.SkuSupplierView 契约视图):
 *     每 SKU 取最近一笔非 DRAFT 采购单的明细行快照。普通 class(MyBatis setter 映射,record 需构造器映射不引入)
 */
@Data
public class SkuSupplierRow {

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 供应商ID(supplier.id) */
    private Long supplierId;

    /** 供应商名称(supplier.name) */
    private String supplierName;

    /** 最新采购单价(本位币;历史行为无价单时可能为 null) */
    private BigDecimal lastPrice;

    /** 最近采购单号(溯源) */
    private String lastPoNo;

    /** 最近采购单创建时间 */
    private LocalDateTime lastPoAt;
}
