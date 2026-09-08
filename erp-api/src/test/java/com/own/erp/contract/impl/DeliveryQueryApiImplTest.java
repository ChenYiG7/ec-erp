package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.DeliveryQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.fulfill.request.query.DeliveryOrderQuery;
import com.own.erp.fulfill.response.DeliveryOrderItemResponse;
import com.own.erp.fulfill.response.DeliveryOrderResponse;
import com.own.erp.fulfill.service.DeliveryOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : DeliveryQueryApiImpl 单测(#6 tools 扩容,AIR:mock DeliveryOrderService,不依赖数据库):
 *     过滤/分页参数映射(含默认归一)、行视图/明细显式映射、发货单不存在透传 null
 */
class DeliveryQueryApiImplTest {

    private DeliveryOrderService deliveryOrderService;
    private DeliveryQueryApi deliveryQueryApi;

    @BeforeEach
    void setUp() {
        deliveryOrderService = mock(DeliveryOrderService.class);
        deliveryQueryApi = new DeliveryQueryApiImpl(deliveryOrderService);
    }

    private DeliveryOrderResponse delivery() {
        return DeliveryOrderResponse.builder()
                .id(1L)
                .deliveryNo("DL-20260908-001")
                .orderId(2L)
                .shopId(3L)
                .warehouseId(4L)
                .type("MANUAL")
                .status("SHIPPED")
                .shipByTime(LocalDateTime.of(2026, 9, 9, 0, 0))
                .logisticsCompany("顺丰")
                .trackingNo("SF123456")
                .shippedAt(LocalDateTime.of(2026, 9, 8, 9, 0))
                .waybillUrl("http://waybill/1.pdf")
                .build()
                .withItems(List.of(DeliveryOrderItemResponse.builder()
                        .id(11L)
                        .deliveryId(1L)
                        .orderItemId(22L)
                        .skuId(5L)
                        .shipQty(3)
                        .build()));
    }

    @Test
    void pageDeliveriesMapsFilterAndPagingIntoDomainQuery() {
        when(deliveryOrderService.page(any())).thenReturn(new Page<>(1, 20, 0));

        deliveryQueryApi.pageDeliveries(DeliveryQueryApi.DeliveryFilter.builder()
                .deliveryNo("DL-1").orderId(2L).shopId(3L).status("PENDING")
                .pageNo(2).pageSize(50).build());

        ArgumentCaptor<DeliveryOrderQuery> captor = ArgumentCaptor.forClass(DeliveryOrderQuery.class);
        verify(deliveryOrderService).page(captor.capture());
        DeliveryOrderQuery query = captor.getValue();
        assertEquals("DL-1", query.getDeliveryNo());
        assertEquals(2L, query.getOrderId());
        assertEquals(3L, query.getShopId());
        assertEquals("PENDING", query.getStatus());
        assertEquals(2, query.getPageNo());
        assertEquals(50, query.getPageSize());
    }

    @Test
    void pageDeliveriesNormalizesMissingPaging() {
        when(deliveryOrderService.page(any())).thenReturn(new Page<>(1, 20, 0));

        deliveryQueryApi.pageDeliveries(DeliveryQueryApi.DeliveryFilter.builder().build());

        ArgumentCaptor<DeliveryOrderQuery> captor = ArgumentCaptor.forClass(DeliveryOrderQuery.class);
        verify(deliveryOrderService).page(captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void pageDeliveriesMapsRowsAndTotal() {
        Page<DeliveryOrderResponse> page = new Page<>(1, 20, 3);
        page.setRecords(List.of(delivery()));
        when(deliveryOrderService.page(any())).thenReturn(page);

        QueryPage<DeliveryQueryApi.DeliveryView> result = deliveryQueryApi.pageDeliveries(
                DeliveryQueryApi.DeliveryFilter.builder().build());

        assertEquals(3, result.total());
        DeliveryQueryApi.DeliveryView view = result.list().get(0);
        assertEquals(1L, view.id());
        assertEquals("DL-20260908-001", view.deliveryNo());
        assertEquals(2L, view.orderId());
        assertEquals(3L, view.shopId());
        assertEquals("SHIPPED", view.status());
        assertEquals("SF123456", view.trackingNo());
        assertEquals(LocalDateTime.of(2026, 9, 8, 9, 0), view.shippedAt());
    }

    @Test
    void getDeliveryDetailMapsItems() {
        when(deliveryOrderService.getById(1L)).thenReturn(delivery());

        DeliveryQueryApi.DeliveryDetail detail = deliveryQueryApi.getDeliveryDetail(1L);

        assertEquals(1L, detail.order().id());
        assertEquals(1, detail.items().size());
        DeliveryQueryApi.DeliveryDetail.Item item = detail.items().get(0);
        assertEquals(11L, item.deliveryItemId());
        assertEquals(22L, item.orderItemId());
        assertEquals(5L, item.skuId());
        assertEquals(3, item.shipQty());
    }

    @Test
    void getDeliveryDetailReturnsNullWhenMissing() {
        when(deliveryOrderService.getById(99L)).thenReturn(null);
        assertNull(deliveryQueryApi.getDeliveryDetail(99L));
    }

    @Test
    void getDeliveryDetailToleratesNullItems() {
        when(deliveryOrderService.getById(1L)).thenReturn(delivery().withItems(null));

        assertTrue(deliveryQueryApi.getDeliveryDetail(1L).items().isEmpty());
    }
}
