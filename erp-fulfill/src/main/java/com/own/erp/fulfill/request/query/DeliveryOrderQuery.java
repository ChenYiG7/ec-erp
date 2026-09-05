package com.own.erp.fulfill.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 发货单分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定);
 *     #11 激活:补发货单号模糊/订单/店铺/状态过滤(pageNo/pageSize 已由 PageQuery 提供并钳制 ≤500)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryOrderQuery extends PageQuery {

    /** 发货单号,模糊匹配 */
    private String deliveryNo;

    /** 平台订单ID,精确 */
    private Long orderId;

    /** 店铺ID,精确 */
    private Long shopId;

    /** 状态,精确:PENDING/SHIPPED/DELIVERED/CANCELLED */
    private String status;
}
