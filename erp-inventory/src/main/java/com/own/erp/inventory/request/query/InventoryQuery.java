package com.own.erp.inventory.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 库存分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定;pageNo/pageSize 钳制 ≤500)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryQuery extends PageQuery {

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 仓库ID(warehouse.id) */
    private Long warehouseId;
}
