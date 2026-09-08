package com.own.erp.contract.impl;

import com.own.erp.order.service.OrderSalesDailyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : SalesQueryApiImpl 单测(#6 销量数据面,AIR:mock 域服务):委托透传
 */
class SalesQueryApiImplTest {

    private OrderSalesDailyService orderSalesDailyService;
    private SalesQueryApiImpl impl;

    @BeforeEach
    void setUp() {
        orderSalesDailyService = mock(OrderSalesDailyService.class);
        impl = new SalesQueryApiImpl(orderSalesDailyService);
    }

    @Test
    void delegatesToDomainService() {
        when(orderSalesDailyService.sumQtyBySku(List.of(1L, 2L), 30)).thenReturn(Map.of(1L, 60));

        Map<Long, Integer> result = impl.sumQtyBySku(List.of(1L, 2L), 30);

        assertEquals(Map.of(1L, 60), result);
        verify(orderSalesDailyService).sumQtyBySku(List.of(1L, 2L), 30);
    }

    @Test
    void delegatesDailySeriesToDomainService() {
        // #6 补货算法 V2:逐日序列委托透传
        Map<Long, Map<java.time.LocalDate, Integer>> series =
                Map.of(1L, Map.of(java.time.LocalDate.of(2026, 9, 8), 5));
        when(orderSalesDailyService.listDailyQtyBySku(List.of(1L), 30)).thenReturn(series);

        assertEquals(series, impl.listDailyQtyBySku(List.of(1L), 30));
        verify(orderSalesDailyService).listDailyQtyBySku(List.of(1L), 30);
    }
}
