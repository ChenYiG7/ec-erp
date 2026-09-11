package com.own.erp.contract.impl;

import com.own.erp.contract.QueryPage;
import com.own.erp.contract.ReportQueryApi;
import com.own.erp.report.report.SkuTrendResponse;
import com.own.erp.report.report.SkuTrendRow;
import com.own.erp.report.report.SkuTrendSummary;
import com.own.erp.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/10
 * @Description : ReportQueryApiImpl 单测(#6 第八类,编排胶水,AIR:mock 域服务):
 *     域 record → 契约行视图显式逐字段映射、内存分页(零新 SQL)、空列表透传、
 *     空 skuId 短路不触达域服务
 */
class ReportQueryApiImplTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);

    private ReportService reportService;
    private ReportQueryApiImpl impl;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        impl = new ReportQueryApiImpl(reportService);
    }

    @Test
    void salesDailyMapsFieldsAndPagesInMemory() {
        LocalDate d1 = DAY.minusDays(2);
        LocalDate d2 = DAY.minusDays(1);
        LocalDate d3 = DAY;
        when(reportService.salesDaily(null, null)).thenReturn(List.of(
                new com.own.erp.report.report.SalesDailyRow(d1, 10, 2),
                new com.own.erp.report.report.SalesDailyRow(d2, 20, 3),
                new com.own.erp.report.report.SalesDailyRow(d3, 30, 4)));

        QueryPage<ReportQueryApi.SalesDailyRow> page = impl.salesDailySummary(
                ReportQueryApi.ReportSalesQuery.builder().pageNo(2).pageSize(2).build());

        // total = 全量行数(3),本页 = 第 2 页 2 条 → 仅第 3 行
        assertEquals(3, page.total());
        assertEquals(1, page.list().size());
        ReportQueryApi.SalesDailyRow row = page.list().get(0);
        assertEquals(d3, row.statDate());
        assertEquals(30, row.totalQty());
        assertEquals(4, row.skuCount());
        verify(reportService).salesDaily(null, null);
    }

    @Test
    void salesDailyEmptyReturnsEmptyPage() {
        when(reportService.salesDaily(null, null)).thenReturn(List.of());

        QueryPage<ReportQueryApi.SalesDailyRow> page = impl.salesDailySummary(
                ReportQueryApi.ReportSalesQuery.builder().build());

        assertEquals(0, page.total());
        assertTrue(page.list().isEmpty());
    }

    @Test
    void skuTopMapsFieldsAndPassesNormalizedLimit() {
        when(reportService.salesSku(null, null, 20)).thenReturn(List.of(
                new com.own.erp.report.report.SalesSkuRow(7L, "SKU-7", "防晒霜", 88)));

        List<ReportQueryApi.SkuSalesRow> rows = impl.skuSalesTop(
                ReportQueryApi.ReportSkuQuery.builder().build());

        assertEquals(1, rows.size());
        assertEquals(7L, rows.get(0).skuId());
        assertEquals("SKU-7", rows.get(0).skuCode());
        assertEquals("防晒霜", rows.get(0).productName());
        assertEquals(88, rows.get(0).totalQty());
        // limit 未传 → topN() 归一 20(与契约 record 默认口径一致)
        verify(reportService).salesSku(null, null, 20);
    }

    @Test
    void skuTrendMapsPointsFromDomainTrend() {
        when(reportService.goodsTrend(eq(5L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new SkuTrendResponse(null, List.of(
                        new SkuTrendRow(DAY.minusDays(1), 0, null),
                        new SkuTrendRow(DAY, 12, 34)),
                        new SkuTrendSummary(12, 1, 34, DAY)));

        List<ReportQueryApi.SkuTrendPoint> points = impl.skuTrend(5L, null);

        assertEquals(2, points.size());
        assertEquals(DAY.minusDays(1), points.get(0).statDate());
        assertEquals(0, points.get(0).qtySold());
        // 快照缺失断点:库存 null 原样透传(不猜 0)
        assertEquals(null, points.get(0).qtyOnHand());
        assertEquals(12, points.get(1).qtySold());
        assertEquals(34, points.get(1).qtyOnHand());
    }

    @Test
    void skuTrendNullSkuIdShortCircuits() {
        List<ReportQueryApi.SkuTrendPoint> points = impl.skuTrend(null, 30);

        assertTrue(points.isEmpty());
        verify(reportService, never()).goodsTrend(any(), any(), any());
    }

    @Test
    void inventorySnapshotMapsFieldsAndPagesInMemory() {
        when(reportService.snapshot(DAY)).thenReturn(List.of(
                new com.own.erp.report.report.InventorySnapshotRow(DAY, 7L, "SKU-7", "防晒霜",
                        2L, "宁波仓", 100, 5, 8, 95)));

        QueryPage<ReportQueryApi.InventorySnapshotRow> page = impl.inventorySnapshotSummary(
                ReportQueryApi.ReportInvQuery.builder().date(DAY).build());

        assertEquals(1, page.total());
        ReportQueryApi.InventorySnapshotRow row = page.list().get(0);
        assertEquals(DAY, row.statDate());
        assertEquals(7L, row.skuId());
        assertEquals("SKU-7", row.skuCode());
        assertEquals("防晒霜", row.productName());
        assertEquals(2L, row.warehouseId());
        assertEquals("宁波仓", row.whName());
        assertEquals(100, row.qtyOnHand());
        assertEquals(5, row.qtyLocked());
        assertEquals(8, row.qtyTransit());
        assertEquals(95, row.qtyAvailable());
        verify(reportService).snapshot(DAY);
    }

    @Test
    void inventorySnapshotNullDateDelegatesLatestDayResolution() {
        when(reportService.snapshot(null)).thenReturn(List.of());

        QueryPage<ReportQueryApi.InventorySnapshotRow> page = impl.inventorySnapshotSummary(
                ReportQueryApi.ReportInvQuery.builder().build());

        assertEquals(0, page.total());
        assertTrue(page.list().isEmpty());
        verify(reportService).snapshot(null);
    }
}
