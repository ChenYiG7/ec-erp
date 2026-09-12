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

    /**
     * 数据权限授权店铺集(#27① 店铺轴):服务器权威装配——Controller/契约实现层在 GET 绑定后
     * 强制覆盖为 CurrentUserApi.currentShopIds()(不信任前端自带值);
     * null=不限(admin);非空=店铺 IN 过滤;空列表=不可见任何店铺数据(Service 短路零结果)
     */
    private java.util.List<Long> shopIds;

    /** 发货单号,模糊匹配 */
    private String deliveryNo;

    /** 平台订单ID,精确 */
    private Long orderId;

    /** 店铺ID,精确 */
    private Long shopId;

    /** 状态,精确:PENDING/SHIPPED/DELIVERED/CANCELLED */
    private String status;
}
