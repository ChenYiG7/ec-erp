package com.own.erp.ai.tools;

import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.contract.ProfitQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.ReportQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/10
 * @Description : ReportTools 单测(#6 第八类,AIR:mock 只读契约,不出网):
 *     工具面裁剪硬上限(表格页大小/排行 limit 钳 20、趋势天数钳 30)、利润入参日期语义转换
 *     (工具面"含首含尾" → 契约面 dateTo 不含)、日期缺省透传 null 交由域服务兜底
 */
class ReportToolsTest {

    private ReportQueryApi reportQueryApi;
    private ProfitQueryApi profitQueryApi;
    private ReportTools tools;

    @BeforeEach
    void setUp() {
        reportQueryApi = mock(ReportQueryApi.class);
        profitQueryApi = mock(ProfitQueryApi.class);
        tools = new ReportTools(reportQueryApi, profitQueryApi);
    }

    @Test
    void skuTopClampsLimitToToolHardCap() {
        when(reportQueryApi.skuSalesTop(any())).thenReturn(List.of());

        tools.reportSkuSalesTop(null, null, 999);

        ArgumentCaptor<ReportQueryApi.ReportSkuQuery> captor =
                ArgumentCaptor.forClass(ReportQueryApi.ReportSkuQuery.class);
        verify(reportQueryApi).skuSalesTop(captor.capture());
        assertEquals(20, captor.getValue().topN());
    }

    @Test
    void skuTopKeepsLimitUnderCap() {
        when(reportQueryApi.skuSalesTop(any())).thenReturn(List.of());

        tools.reportSkuSalesTop(null, null, 5);

        ArgumentCaptor<ReportQueryApi.ReportSkuQuery> captor =
                ArgumentCaptor.forClass(ReportQueryApi.ReportSkuQuery.class);
        verify(reportQueryApi).skuSalesTop(captor.capture());
        assertEquals(5, captor.getValue().topN());
    }

    @Test
    void trendDaysDefaultsToThirtyAndClampsOverflow() {
        when(reportQueryApi.skuTrend(any(), any())).thenReturn(List.of());

        tools.reportSkuTrend(9L, null);
        tools.reportSkuTrend(9L, 999);
        tools.reportSkuTrend(9L, 7);

        // 缺省=30;超上限 999 钳 30(两次同参调用);区间内 7 原样透传
        verify(reportQueryApi, times(2)).skuTrend(9L, 30);
        verify(reportQueryApi).skuTrend(9L, 7);
    }

    @Test
    void dailyNullWindowPassesThroughToDomainDefault() {
        when(reportQueryApi.salesDailySummary(any())).thenReturn(QueryPage.of(List.of(), 0));

        tools.reportSalesDaily(null, null, null, null);

        ArgumentCaptor<ReportQueryApi.ReportSalesQuery> captor =
                ArgumentCaptor.forClass(ReportQueryApi.ReportSalesQuery.class);
        verify(reportQueryApi).salesDailySummary(captor.capture());
        // 空窗口交由域服务兜底(近 30 天),工具层不重复实现窗口语义
        assertNull(captor.getValue().dateFrom());
        assertNull(captor.getValue().dateTo());
        assertEquals(1, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void snapshotNullDatePassesThroughForLatestSnapshotDay() {
        when(reportQueryApi.inventorySnapshotSummary(any())).thenReturn(QueryPage.of(List.of(), 0));

        tools.reportInventorySnapshot(null, 3, 50);

        ArgumentCaptor<ReportQueryApi.ReportInvQuery> captor =
                ArgumentCaptor.forClass(ReportQueryApi.ReportInvQuery.class);
        verify(reportQueryApi).inventorySnapshotSummary(captor.capture());
        // date 空 = 最新快照日(域服务解析);页大小 50 被工具硬上限钳到 20
        assertNull(captor.getValue().date());
        assertEquals(3, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void profitConvertsInclusiveDateToToExclusiveBoundary() {
        when(profitQueryApi.summarize(any())).thenReturn(mock(OrderProfitSummary.class));

        tools.reportProfitSummary(1L, "AMAZON", 2L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));

        ArgumentCaptor<OrderProfitQuery> captor = ArgumentCaptor.forClass(OrderProfitQuery.class);
        verify(profitQueryApi).summarize(captor.capture());
        OrderProfitQuery query = captor.getValue();
        assertEquals(1L, query.shopId());
        assertEquals("AMAZON", query.platform());
        assertEquals(2L, query.skuId());
        assertEquals(LocalDate.of(2026, 9, 1).atStartOfDay(), query.dateFrom());
        // 工具面对模型暴露"含首含尾"日期,契约 dateTo 语义为不含 → +1 天补齐
        assertEquals(LocalDate.of(2026, 9, 8).atStartOfDay(), query.dateTo());
    }

    @Test
    void profitLeavesNullWindowToDomainDefault() {
        when(profitQueryApi.summarize(any())).thenReturn(mock(OrderProfitSummary.class));

        tools.reportProfitSummary(null, null, null, null, null);

        ArgumentCaptor<OrderProfitQuery> captor = ArgumentCaptor.forClass(OrderProfitQuery.class);
        verify(profitQueryApi).summarize(captor.capture());
        assertNull(captor.getValue().dateFrom());
        assertNull(captor.getValue().dateTo());
    }
}
