package com.own.erp.ai.tools;

import com.own.erp.contract.DeliveryQueryApi;
import com.own.erp.contract.QueryPage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 发货查询工具(#6 tools 扩容,qihang 11 类 checklist Delivery 类,随 DeliveryQueryApi
 *         契约开工具,#11 发货域数据面已齐)。只读铁律(铁律 7):方法全部走 DeliveryQueryApi 只读契约,
 *         无任何写路径;SQL 不经模型拼装(参数即工具入参);契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)
 */
@Component
public class DeliveryTools {

    private final DeliveryQueryApi deliveryQueryApi;

    public DeliveryTools(@Lazy DeliveryQueryApi deliveryQueryApi) {
        this.deliveryQueryApi = deliveryQueryApi;
    }

    @Tool(description = "分页查询发货单列表(按ID倒序)。过滤条件均可选,全部不传=全量分页;返回发货单摘要与总数")
    public QueryPage<DeliveryQueryApi.DeliveryView> listDeliveries(
            @ToolParam(required = false, description = "发货单号,模糊匹配") String deliveryNo,
            @ToolParam(required = false, description = "平台订单ID(shop_order.id),精确过滤") Long orderId,
            @ToolParam(required = false, description = "店铺ID,精确过滤") Long shopId,
            @ToolParam(required = false, description = "发货单状态:PENDING/SHIPPED/DELIVERED/CANCELLED") String status,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大100") Integer pageSize) {
        return deliveryQueryApi.pageDeliveries(DeliveryQueryApi.DeliveryFilter.builder()
                .deliveryNo(deliveryNo)
                .orderId(orderId)
                .shopId(shopId)
                .status(status)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(pageSize == null ? 0 : pageSize)
                .build());
    }

    @Tool(description = "按发货单ID查发货单详情(含发货明细行:订单行/SKU/发货数量;shippedAt 为空表示未发货)。发货单不存在返回 null")
    public DeliveryQueryApi.DeliveryDetail getDelivery(
            @ToolParam(description = "发货单ID(delivery_order.id)") Long deliveryId) {
        return deliveryQueryApi.getDeliveryDetail(deliveryId);
    }
}
