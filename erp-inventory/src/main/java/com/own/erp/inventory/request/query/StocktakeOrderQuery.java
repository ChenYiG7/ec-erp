package com.own.erp.inventory.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点单分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     过滤:盘点单号模糊/仓库/状态(pageNo/pageSize 已由 PageQuery 提供并钳制 ≤500)
 */
/**
 * 保持 class(模型可变性分级 docs/07 §1 的继承例外):record 不能继承类,
 * 而 XxxQuery 必须继承 PageQuery 的钳制分页——可变性豁免,后续 PageQuery 重构再议
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StocktakeOrderQuery extends PageQuery {

    /** 盘点单号(模糊) */
    private String stocktakeNo;

    /** 盘点仓ID(warehouse.id) */
    private Long warehouseId;

    /** 状态:DRAFT/COUNTING/PENDING_ADJUST/ADJUSTED/CLOSED/CANCELED */
    private String status;
}
