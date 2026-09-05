package com.own.erp.order.request.query;

import com.own.erp.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 平台订单分页查询入参(docs/07 §1:XxxQuery 继承 PageQuery,GET 查询串自动绑定;pageNo/pageSize 钳制 ≤500)
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopOrderQuery extends PageQuery {

    /** 店铺ID(shop.id) */
    private Long shopId;

    /** PlatformType枚举名:TAOBAO/AMAZON/... */
    private String platform;

    /** WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED */
    private String orderStatus;
}
