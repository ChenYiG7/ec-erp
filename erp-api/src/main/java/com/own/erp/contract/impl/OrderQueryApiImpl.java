package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.order.request.query.ShopOrderQuery;
import com.own.erp.order.response.ShopOrderItemResponse;
import com.own.erp.order.response.ShopOrderResponse;
import com.own.erp.order.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : OrderQueryApi 实现(#6 三期 AI 地基,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 工具取数委托 erp-order ShopOrderService,过滤/分页参数在域 Query 侧沿用既有钳制;
 *         entity→契约 record 显式逐字段映射(经域 Response 中转,禁反射拷贝)
 */
@Component
@RequiredArgsConstructor
public class OrderQueryApiImpl implements OrderQueryApi {

    private final ShopOrderService shopOrderService;

    @Override
    public QueryPage<OrderView> pageOrders(OrderFilter filter) {
        ShopOrderQuery query = new ShopOrderQuery();
        query.setShopId(filter.shopId());
        query.setPlatform(filter.platform());
        query.setOrderStatus(filter.orderStatus());
        query.setPageNo(filter.page());
        query.setPageSize(filter.size());
        Page<ShopOrderResponse> page = shopOrderService.page(query);
        List<OrderView> list = page.getRecords().stream().map(OrderQueryApiImpl::toView).toList();
        return QueryPage.of(list, page.getTotal());
    }

    @Override
    public OrderDetail getOrderDetail(Long orderId) {
        ShopOrderResponse order = shopOrderService.getById(orderId);
        if (order == null) {
            return null;
        }
        List<OrderDetail.Item> items = order.items() == null ? List.of() : order.items().stream()
                .map(OrderQueryApiImpl::toItem)
                .toList();
        return OrderDetail.builder().order(toView(order)).items(items).build();
    }

    /** Response → 行视图显式逐字段映射(契约不依赖域 Response 类型,漏字段编译期可见) */
    private static OrderView toView(ShopOrderResponse o) {
        return OrderView.builder()
                .id(o.id())
                .shopId(o.shopId())
                .platform(o.platform())
                .platformOrderId(o.platformOrderId())
                .orderStatus(o.orderStatus())
                .fulfillmentChannel(o.fulfillmentChannel())
                .orderTime(o.orderTime())
                .paidTime(o.paidTime())
                .currency(o.currency())
                .exchangeRate(o.exchangeRate())
                .orderAmount(o.orderAmount())
                .shippingFee(o.shippingFee())
                .discountAmount(o.discountAmount())
                .build();
    }

    private static OrderDetail.Item toItem(ShopOrderItemResponse i) {
        return OrderDetail.Item.builder()
                .orderItemId(i.id())
                .skuId(i.skuId())
                .platformSku(i.platformSku())
                .productName(i.productName())
                .quantity(i.quantity())
                .unitPrice(i.unitPrice())
                .itemAmount(i.itemAmount())
                .currency(i.currency())
                .build();
    }
}
