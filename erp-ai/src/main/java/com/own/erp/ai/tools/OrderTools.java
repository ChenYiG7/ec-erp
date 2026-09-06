package com.own.erp.ai.tools;

import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.QueryPage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 订单查询工具(#6,启航 11 类 checklist:Order 类已开;Shop/Purchase/Delivery/Report 等
 *         余量待随对应查询契约扩容,一类一文件,docs/07 §9)。
 *         只读铁律(铁律 7):方法全部走 OrderQueryApi 只读契约,无任何写路径;SQL 不经模型拼装(参数即工具入参);
 *         契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)
 */
@Component
public class OrderTools {

    private final OrderQueryApi orderQueryApi;

    public OrderTools(@Lazy OrderQueryApi orderQueryApi) {
        this.orderQueryApi = orderQueryApi;
    }

    @Tool(description = "分页查询平台订单列表(按下单时间倒序)。过滤条件均可选,全部不传=全量分页;返回订单摘要与总数")
    public QueryPage<OrderQueryApi.OrderView> listOrders(
            @ToolParam(required = false, description = "店铺ID,精确过滤") Long shopId,
            @ToolParam(required = false, description = "平台枚举名:TAOBAO/AMAZON等,精确过滤") String platform,
            @ToolParam(required = false, description = "订单状态:WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED") String orderStatus,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大100") Integer pageSize) {
        return orderQueryApi.pageOrders(OrderQueryApi.OrderFilter.builder()
                .shopId(shopId)
                .platform(platform)
                .orderStatus(orderStatus)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(pageSize == null ? 0 : pageSize)
                .build());
    }

    @Tool(description = "按订单ID查订单详情(含全部购买明细行;skuId 为 null 表示该行未绑定内部SKU)。订单不存在返回 null")
    public OrderQueryApi.OrderDetail getOrder(
            @ToolParam(description = "订单ID(shop_order.id)") Long orderId) {
        return orderQueryApi.getOrderDetail(orderId);
    }
}
