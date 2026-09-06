package com.own.erp.order.service;

import com.own.erp.order.mapper.OrderSalesDailyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : OrderSalesDailyService 单测(#6 销量数据面,AIR:mock Mapper+固定 Clock):
 *     窗口重算委托透传、销量合计行映射(Long/BigDecimal 兼容取数)、空入参/非法窗口短路不触库
 */
class OrderSalesDailyServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);
    private static final Clock CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));

    private OrderSalesDailyMapper orderSalesDailyMapper;
    private OrderSalesDailyService service;

    @BeforeEach
    void setUp() {
        orderSalesDailyMapper = mock(OrderSalesDailyMapper.class);
        service = new OrderSalesDailyService(orderSalesDailyMapper, CLOCK);
    }

    @Test
    void rebuildWindowDelegatesWithDates() {
        when(orderSalesDailyMapper.upsertWindow(TODAY.minusDays(29), TODAY.plusDays(1))).thenReturn(7);

        int affected = service.rebuildWindow(TODAY.minusDays(29), TODAY.plusDays(1));

        assertEquals(7, affected);
        verify(orderSalesDailyMapper).upsertWindow(TODAY.minusDays(29), TODAY.plusDays(1));
    }

    @Test
    void sumQtyBySkuMapsRowsAndTrailingWindow() {
        // SUM 聚合列 JDBC 侧可能回 Long/BigDecimal,统一按 Number 取数
        when(orderSalesDailyMapper.sumQtySince(TODAY.minusDays(29), List.of(1L, 2L))).thenReturn(List.of(
                Map.of("skuId", 1L, "qtySold", 60L),
                Map.of("skuId", 2L, "qtySold", new java.math.BigDecimal("45"))));

        Map<Long, Integer> result = service.sumQtyBySku(List.of(1L, 2L), 30);

        assertEquals(Map.of(1L, 60, 2L, 45), result);
        verify(orderSalesDailyMapper).sumQtySince(TODAY.minusDays(29), List.of(1L, 2L));
    }

    @Test
    void sumQtyBySkuShortCircuitsOnEmptyOrInvalidInput() {
        assertEquals(Map.of(), service.sumQtyBySku(null, 30));
        assertEquals(Map.of(), service.sumQtyBySku(List.of(), 30));
        assertEquals(Map.of(), service.sumQtyBySku(List.of(1L), 0));

        verifyNoInteractions(orderSalesDailyMapper);
    }
}
