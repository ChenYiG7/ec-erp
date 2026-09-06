package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.order.request.query.ShopOrderQuery;
import com.own.erp.order.response.ShopOrderItemResponse;
import com.own.erp.order.response.ShopOrderResponse;
import com.own.erp.order.service.ShopOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : OrderQueryApiImpl 单测(#6,AIR:mock ShopOrderService,不依赖数据库):
 *     过滤/分页参数映射(含默认归一)、行视图/明细显式映射、订单不存在透传 null
 */
class OrderQueryApiImplTest {

    private ShopOrderService shopOrderService;
    private OrderQueryApi orderQueryApi;

    @BeforeEach
    void setUp() {
        shopOrderService = mock(ShopOrderService.class);
        orderQueryApi = new OrderQueryApiImpl(shopOrderService);
    }

    private ShopOrderResponse order() {
        return ShopOrderResponse.builder()
                .id(1L)
                .shopId(2L)
                .platform("AMAZON")
                .platformOrderId("PO-1")
                .orderStatus("WAIT_SHIP")
                .fulfillmentChannel("SELF_FULFILL")
                .orderTime(LocalDateTime.of(2026, 9, 1, 10, 0))
                .currency("USD")
                .exchangeRate(new BigDecimal("7.10000000"))
                .orderAmount(new BigDecimal("100.0000"))
                .shippingFee(new BigDecimal("5.0000"))
                .discountAmount(BigDecimal.ZERO)
                .build()
                .withItems(List.of(ShopOrderItemResponse.builder()
                        .id(11L)
                        .skuId(null)
                        .platformSku("SELLER-SKU-1")
                        .productName("平台商品A")
                        .quantity(2)
                        .unitPrice(new BigDecimal("50.0000"))
                        .itemAmount(new BigDecimal("100.0000"))
                        .currency("USD")
                        .build()));
    }

    @Test
    void pageOrdersMapsFilterAndPagingIntoDomainQuery() {
        when(shopOrderService.page(any())).thenReturn(new Page<>(1, 20, 0));

        orderQueryApi.pageOrders(OrderQueryApi.OrderFilter.builder()
                .shopId(2L).platform("AMAZON").orderStatus("WAIT_SHIP")
                .pageNo(3).pageSize(50).build());

        ArgumentCaptor<ShopOrderQuery> captor = ArgumentCaptor.forClass(ShopOrderQuery.class);
        verify(shopOrderService).page(captor.capture());
        ShopOrderQuery query = captor.getValue();
        assertEquals(2L, query.getShopId());
        assertEquals("AMAZON", query.getPlatform());
        assertEquals("WAIT_SHIP", query.getOrderStatus());
        assertEquals(3, query.getPageNo());
        assertEquals(50, query.getPageSize());
    }

    @Test
    void pageOrdersNormalizesMissingPaging() {
        when(shopOrderService.page(any())).thenReturn(new Page<>(1, 20, 0));

        orderQueryApi.pageOrders(OrderQueryApi.OrderFilter.builder().build());

        ArgumentCaptor<ShopOrderQuery> captor = ArgumentCaptor.forClass(ShopOrderQuery.class);
        verify(shopOrderService).page(captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void pageOrdersMapsRowsAndTotal() {
        Page<ShopOrderResponse> page = new Page<>(1, 20, 42);
        page.setRecords(List.of(order()));
        when(shopOrderService.page(any())).thenReturn(page);

        QueryPage<OrderQueryApi.OrderView> result = orderQueryApi.pageOrders(
                OrderQueryApi.OrderFilter.builder().build());

        assertEquals(42, result.total());
        assertEquals(1, result.list().size());
        OrderQueryApi.OrderView view = result.list().get(0);
        assertEquals(1L, view.id());
        assertEquals(2L, view.shopId());
        assertEquals("AMAZON", view.platform());
        assertEquals("PO-1", view.platformOrderId());
        assertEquals("WAIT_SHIP", view.orderStatus());
        assertEquals(0, new BigDecimal("100.0000").compareTo(view.orderAmount()));
        assertEquals("USD", view.currency());
    }

    @Test
    void getOrderDetailReturnsNullWhenMissing() {
        when(shopOrderService.getById(404L)).thenReturn(null);
        assertNull(orderQueryApi.getOrderDetail(404L));
    }

    @Test
    void getOrderDetailMapsOrderAndItemsIncludingUnboundSkuRows() {
        when(shopOrderService.getById(1L)).thenReturn(order());

        OrderQueryApi.OrderDetail detail = orderQueryApi.getOrderDetail(1L);

        assertNotNull(detail);
        assertEquals(1L, detail.order().id());
        assertEquals("WAIT_SHIP", detail.order().orderStatus());
        assertEquals(1, detail.items().size());
        OrderQueryApi.OrderDetail.Item item = detail.items().get(0);
        assertEquals(11L, item.orderItemId());
        assertNull(item.skuId());
        assertEquals("SELLER-SKU-1", item.platformSku());
        assertEquals(2, item.quantity());
        assertTrue(new BigDecimal("100.0000").compareTo(item.itemAmount()) == 0);
    }
}
