package com.own.erp.contract.impl;

import com.own.erp.contract.CurrentUserApi;
import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitRow;
import com.own.erp.contract.QueryPage;
import com.own.erp.finance.service.ProfitQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : ProfitQueryApiImpl 单测(#27① 数据权限,AIR:mock Service/CurrentUserApi 不依赖数据库):
 *     HTTP 链路(前端利润页 + AI 工具)经本实现强制装配 currentShopIds() 重建查询入参;
 *     委托语义(返回值透传)不在本测面——ProfitQueryService 侧已覆盖
 */
class ProfitQueryApiImplTest {

    private ProfitQueryService profitQueryService;
    private CurrentUserApi currentUserApi;
    private ProfitQueryApiImpl profitQueryApi;

    @BeforeEach
    void setUp() {
        profitQueryService = mock(ProfitQueryService.class);
        currentUserApi = mock(CurrentUserApi.class);
        when(currentUserApi.currentShopIds()).thenReturn(null);
        profitQueryApi = new ProfitQueryApiImpl(profitQueryService, currentUserApi);
    }

    private OrderProfitQuery query() {
        return new OrderProfitQuery(2L, "AMAZON", 11L, null, null, 1, 50, null);
    }

    @Test
    void pageOrderProfitForcesShopScopeOverCallerSuppliedValue() {
        when(currentUserApi.currentShopIds()).thenReturn(List.of(7L, 8L));
        when(profitQueryService.page(any())).thenReturn(QueryPage.of(List.of(), 0));

        profitQueryApi.pageOrderProfit(query());

        ArgumentCaptor<OrderProfitQuery> captor = ArgumentCaptor.forClass(OrderProfitQuery.class);
        verify(profitQueryService).page(captor.capture());
        assertEquals(List.of(7L, 8L), captor.getValue().shopIds());
        // 其余过滤参数原样保留
        assertEquals(2L, captor.getValue().shopId());
        assertEquals("AMAZON", captor.getValue().platform());
    }

    @Test
    void summarizeTrendAndRankAllCarryShopScope() {
        when(currentUserApi.currentShopIds()).thenReturn(List.of(7L));
        when(profitQueryService.summarize(any())).thenReturn(null);
        when(profitQueryService.listDailyTrend(any())).thenReturn(List.of());
        when(profitQueryService.listSkuProfitRank(any(), anyInt())).thenReturn(List.of());

        profitQueryApi.summarize(query());
        profitQueryApi.listDailyTrend(query());
        profitQueryApi.listSkuProfitRank(query(), 10);

        ArgumentCaptor<OrderProfitQuery> captor = ArgumentCaptor.forClass(OrderProfitQuery.class);
        verify(profitQueryService).summarize(captor.capture());
        verify(profitQueryService).listDailyTrend(captor.capture());
        verify(profitQueryService).listSkuProfitRank(captor.capture(), anyInt());
        captor.getAllValues().forEach(q -> assertEquals(List.of(7L), q.shopIds()));
    }

    @Test
    void adminUnrestrictedScopeStaysNull() {
        when(currentUserApi.currentShopIds()).thenReturn(null);
        when(profitQueryService.page(any())).thenReturn(QueryPage.of(List.<OrderProfitRow>of(), 0));

        profitQueryApi.pageOrderProfit(query());

        ArgumentCaptor<OrderProfitQuery> captor = ArgumentCaptor.forClass(OrderProfitQuery.class);
        verify(profitQueryService).page(captor.capture());
        assertEquals(null, captor.getValue().shopIds());
    }
}
