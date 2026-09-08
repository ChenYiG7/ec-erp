package com.own.erp.contract.impl;

import com.own.erp.contract.ShopOrderApi;
import com.own.erp.order.response.ShopOrderResponse;
import com.own.erp.order.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : ShopOrderApi 实现(接口模块方案的编排胶水,收口 erp-api,#11):
 *         erp-fulfill 建单校验/发足推进取数走 erp-order ShopOrderService(docs/07 §2.2)。
 *         findDeliveryView 过滤 sku_id 未绑定的明细行(不参与发货与发足判定,2026-09-04 拍板)
 */
@Component
@RequiredArgsConstructor
public class ShopOrderApiImpl implements ShopOrderApi {

    private final ShopOrderService shopOrderService;

    @Override
    public OrderDeliveryView findDeliveryView(Long orderId) {
        ShopOrderResponse order = shopOrderService.getById(orderId);
        if (order == null) {
            return null;
        }
        List<OrderDeliveryView.Item> items = order.items() == null ? List.of() : order.items().stream()
                .filter(item -> item.skuId() != null)
                .map(item -> OrderDeliveryView.Item.builder()
                        .orderItemId(item.id())
                        .platformOrderItemId(item.platformOrderItemId())
                        .skuId(item.skuId())
                        .quantity(item.quantity())
                        .build())
                .toList();
        return OrderDeliveryView.builder()
                .orderId(order.id())
                .shopId(order.shopId())
                .platformOrderId(order.platformOrderId())
                .orderStatus(order.orderStatus())
                .fulfillmentChannel(order.fulfillmentChannel())
                .items(items)
                .build();
    }

    @Override
    public boolean casOrderStatus(Long orderId, String fromStatus, String toStatus) {
        return shopOrderService.casOrderStatus(orderId, fromStatus, toStatus);
    }

    @Override
    public Long findIdByPlatformOrderId(Long shopId, String platformOrderId) {
        return shopOrderService.findIdByPlatformOrderId(shopId, platformOrderId);
    }
}
