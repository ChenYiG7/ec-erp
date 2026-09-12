package com.own.erp.finance.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程发货单分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定):
 *     头程单号模糊/状态/发货仓/目的仓过滤
 */
/**
 * 保持 class(模型可变性分级 docs/07 §1 的继承例外):record 不能继承类,
 * 而 XxxQuery 必须继承 PageQuery 的钳制分页——可变性豁免,后续 PageQuery 重构再议
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FirstLegShipmentQuery extends PageQuery {

    /** 头程单号模糊匹配 */
    private String shipmentNo;

    /** 状态精确:DRAFT/BOXED/SHIPPED/ALLOCATED/CLOSED/CANCELED */
    private String status;

    /** 国内发货仓ID */
    private Long fromWarehouseId;

    /** 目的仓ID */
    private Long toWarehouseId;
}
