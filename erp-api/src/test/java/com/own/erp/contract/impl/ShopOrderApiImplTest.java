package com.own.erp.contract.impl;

import com.own.erp.contract.ShopOrderApi;
import com.own.erp.order.response.ShopOrderItemResponse;
import com.own.erp.order.response.ShopOrderResponse;
import com.own.erp.order.service.ShopOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : ShopOrderApiImpl 单测(#11,AIR:mock ShopOrderService):
 *     发货视图装配(未绑定 sku_id 行过滤)与订单状态条件推进透传
 */
class ShopOrderApiImplTest {

    private ShopOrderService shopOrderService;
    private ShopOrderApi shopOrderApi;

    @BeforeEach
    void setUp() {
        shopOrderService = mock(ShopOrderService.class);
        shopOrderApi = new ShopOrderApiImpl(shopOrderService);
    }

    private ShopOrderResponse order(String status, String channel, List<ShopOrderItemResponse> items) {
        return ShopOrderResponse.builder()
                .id(1L)
                .shopId(2L)
                .orderStatus(status)
                .fulfillmentChannel(channel)
                .build()
                .withItems(items);
    }

    private ShopOrderItemResponse item(Long id, Long skuId, Integer quantity) {
        return ShopOrderItemResponse.builder()
                .id(id)
                .skuId(skuId)
                .quantity(quantity)
                .build();
    }

    @Test
    void findDeliveryViewReturnsNullWhenOrderMissing() {
        when(shopOrderService.getById(404L)).thenReturn(null);
        assertNull(shopOrderApi.findDeliveryView(404L));
    }

    @Test
    void findDeliveryViewFiltersUnboundSkuRowsAndMapsFields() {
        when(shopOrderService.getById(1L)).thenReturn(order("WAIT_SHIP", "SELF_FULFILL", List.of(
                item(11L, 100L, 2),
                item(12L, null, 5))));

        ShopOrderApi.OrderDeliveryView view = shopOrderApi.findDeliveryView(1L);

        assertEquals(1L, view.orderId());
        assertEquals(2L, view.shopId());
        assertEquals("WAIT_SHIP", view.orderStatus());
        assertEquals("SELF_FULFILL", view.fulfillmentChannel());
        // 未绑定 sku_id 的行(12)被过滤,不参与发货与发足判定
        assertEquals(1, view.items().size());
        assertEquals(11L, view.items().get(0).orderItemId());
        assertEquals(100L, view.items().get(0).skuId());
        assertEquals(2, view.items().get(0).quantity());
    }

    @Test
    void casOrderStatusDelegatesToShopOrderService() {
        when(shopOrderService.casOrderStatus(1L, "WAIT_SHIP", "SHIPPED")).thenReturn(true);
        when(shopOrderService.casOrderStatus(1L, "COMPLETED", "SHIPPED")).thenReturn(false);

        assertTrue(shopOrderApi.casOrderStatus(1L, "WAIT_SHIP", "SHIPPED"));
        assertEquals(false, shopOrderApi.casOrderStatus(1L, "COMPLETED", "SHIPPED"));
    }

    @Test
    void findIdByPlatformOrderIdDelegatesAndReturnsNullWhenNotPulled() {
        when(shopOrderService.findIdByPlatformOrderId(2L, "AMZ-PO-1")).thenReturn(1L);
        when(shopOrderService.findIdByPlatformOrderId(2L, "NOT-PULLED")).thenReturn(null);

        assertEquals(1L, shopOrderApi.findIdByPlatformOrderId(2L, "AMZ-PO-1"));
        assertNull(shopOrderApi.findIdByPlatformOrderId(2L, "NOT-PULLED"));
    }
}
