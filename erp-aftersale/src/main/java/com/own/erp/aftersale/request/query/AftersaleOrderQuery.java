package com.own.erp.aftersale.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 售后单分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定)
 *     过滤条件对齐 idx_status/idx_order 索引(#12 人工处理入口按状态筛待处理单)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AftersaleOrderQuery extends PageQuery {

    /** 店铺ID(精确) */
    private Long shopId;

    /** 状态(精确,PENDING/APPROVED/RETURNING/RETURN_RECEIVED/REFUNDED/COMPLETED/REJECTED/CANCELLED) */
    private String status;

    /** 售后类型(精确,REFUND_ONLY/RETURN_REFUND/EXCHANGE/RESEND) */
    private String type;

    /** 关联订单ID(精确) */
    private Long orderId;
}
