package com.own.erp.inventory.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 调拨单分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     过滤:调拨单号模糊/调出仓/调入仓/状态(pageNo/pageSize 已由 PageQuery 提供并钳制 ≤500)
 */
/**
 * 保持 class(模型可变性分级 docs/07 §1 的继承例外):record 不能继承类,
 * 而 XxxQuery 必须继承 PageQuery 的钳制分页——可变性豁免,后续 PageQuery 重构再议
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransferOrderQuery extends PageQuery {

    /** 调拨单号(模糊) */
    private String transferNo;

    /** 调出仓ID(warehouse.id) */
    private Long fromWarehouseId;

    /** 调入仓ID(warehouse.id) */
    private Long toWarehouseId;

    /** 状态:DRAFT/CONFIRMED/CANCELED */
    private String status;
}
