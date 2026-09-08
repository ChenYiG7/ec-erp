package com.own.erp.purchase.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 采购单分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     过滤条件字段按业务在此补(pageNo/pageSize 已由 PageQuery 提供并钳制 ≤500)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderQuery extends PageQuery {

    /** 供应商ID,精确过滤(可选) */
    private Long supplierId;

    /** 收货仓ID,精确过滤(可选) */
    private Long warehouseId;

    /** 状态,精确:DRAFT/AUDITED/PARTIAL_RECEIVED/RECEIVED/CLOSED(可选) */
    private String status;
}
