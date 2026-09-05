package com.own.erp.warehouse.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 仓库分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     过滤条件字段按业务在此补(pageNo/pageSize 已由 PageQuery 提供并钳制 ≤500)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WarehouseQuery extends PageQuery {
}
