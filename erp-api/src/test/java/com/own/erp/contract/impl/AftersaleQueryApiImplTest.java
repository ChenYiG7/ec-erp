package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.aftersale.request.query.AftersaleOrderQuery;
import com.own.erp.aftersale.response.AftersaleOrderResponse;
import com.own.erp.aftersale.service.AftersaleOrderService;
import com.own.erp.contract.AftersaleQueryApi;
import com.own.erp.contract.QueryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AftersaleQueryApiImpl 单测(#6,AIR:mock AftersaleOrderService,不依赖数据库):
 *     四维过滤与分页参数映射、行视图显式映射(不含退货明细——分页不查子表口径)
 */
class AftersaleQueryApiImplTest {

    private AftersaleOrderService aftersaleOrderService;
    private AftersaleQueryApi aftersaleQueryApi;

    @BeforeEach
    void setUp() {
        aftersaleOrderService = mock(AftersaleOrderService.class);
        aftersaleQueryApi = new AftersaleQueryApiImpl(aftersaleOrderService);
    }

    @Test
    void pageAftersalesMapsFilterAndPagingIntoDomainQuery() {
        when(aftersaleOrderService.page(any())).thenReturn(new Page<>(1, 20, 0));

        aftersaleQueryApi.pageAftersales(AftersaleQueryApi.AftersaleFilter.builder()
                .shopId(2L).status("PENDING").type("RETURN_REFUND").orderId(8L)
                .pageNo(1).pageSize(50).build());

        ArgumentCaptor<AftersaleOrderQuery> captor = ArgumentCaptor.forClass(AftersaleOrderQuery.class);
        verify(aftersaleOrderService).page(captor.capture());
        AftersaleOrderQuery query = captor.getValue();
        assertEquals(2L, query.getShopId());
        assertEquals("PENDING", query.getStatus());
        assertEquals("RETURN_REFUND", query.getType());
        assertEquals(8L, query.getOrderId());
        assertEquals(1, query.getPageNo());
        assertEquals(50, query.getPageSize());
    }

    @Test
    void pageAftersalesMapsRowsAndTotal() {
        Page<AftersaleOrderResponse> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(AftersaleOrderResponse.builder()
                .id(1L)
                .aftersaleNo("AS-1")
                .shopId(2L)
                .orderId(8L)
                .warehouseId(null)
                .type("RETURN_REFUND")
                .status("PENDING")
                .refundAmount(new BigDecimal("30.0000"))
                .currency("USD")
                .reason("七天无理由")
                .result(null)
                .createdAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .build()));
        when(aftersaleOrderService.page(any())).thenReturn(page);

        QueryPage<AftersaleQueryApi.AftersaleView> result = aftersaleQueryApi.pageAftersales(
                AftersaleQueryApi.AftersaleFilter.builder().build());

        assertEquals(1, result.total());
        AftersaleQueryApi.AftersaleView view = result.list().get(0);
        assertEquals("AS-1", view.aftersaleNo());
        assertEquals(8L, view.orderId());
        assertEquals("RETURN_REFUND", view.type());
        assertEquals("PENDING", view.status());
        assertEquals(0, new BigDecimal("30.0000").compareTo(view.refundAmount()));
        assertEquals("USD", view.currency());
    }
}
