package com.own.erp.fulfill.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA发货单分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定):
 *     单号模糊/状态/店铺/站点/发货仓过滤(店铺轴数据权限未注入,新域多用户使用后按 #27① 口径补)
 */
/**
 * 保持 class(模型可变性分级 docs/07 §1 的继承例外):record 不能继承类,
 * 而 XxxQuery 必须继承 PageQuery 的钳制分页——可变性豁免,后续 PageQuery 重构再议
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FbaShipmentQuery extends PageQuery {

    /** FBA单号模糊匹配 */
    private String shipmentNo;

    /** 状态精确:DRAFT/BOXED/SHIPPED/RECEIVING/CLOSED/CANCELED */
    private String status;

    /** 店铺ID */
    private Long shopId;

    /** 站点精确(如 US) */
    private String marketplace;

    /** 国内发货仓ID */
    private Long warehouseId;
}
