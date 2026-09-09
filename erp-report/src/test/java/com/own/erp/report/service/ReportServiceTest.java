package com.own.erp.report.service;

import com.own.erp.report.mapper.ReportQueryMapper;
import com.own.erp.report.report.InventorySnapshotRow;
import com.own.erp.report.report.SalesDailyRow;
import com.own.erp.report.report.SalesSkuRow;
import com.own.erp.report.report.SkuOptionRow;
import com.own.erp.report.report.SkuTrendResponse;
import com.own.erp.report.report.SkuTrendRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : ReportService 单测(AIR:mock Mapper,不依赖数据库;#20 报表域 V1)。
 *     必测面:窗口缺省近 30 天/跨度钳制 366 天/倒序窗口回退/快照日缺省取最新(无快照空列表)/
 *     SKU limit 钳制/Excel 导出产物为合法 xlsx 且表头行数正确
 */
class ReportServiceTest {

    private ReportQueryMapper reportQueryMapper;
    private ReportService reportService;

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

    @BeforeEach
    void setUp() {
        reportQueryMapper = mock(ReportQueryMapper.class);
        reportService = new ReportService(reportQueryMapper);
    }

    @Nested
    class Window {

        @Test
        void defaultsToLast30Days() {
            // 缺省:dateTo=今天,dateFrom=今天-29(含起含止共 30 天);期望值动态取 now 防日期耦合
            LocalDate today = LocalDate.now();
            reportService.salesDaily(null, null);
            verify(reportQueryMapper).selectSalesDaily(today.minusDays(29), today);
        }

        @Test
        void clampsSpanTo366Days() {
            LocalDate today = LocalDate.now();
            reportService.salesDaily(today.minusDays(400), today);
            verify(reportQueryMapper).selectSalesDaily(today.minusDays(365), today);
        }

        @Test
        void invertedRangeFallsBackToDefaultWindowEndingAtTo() {
            LocalDate to = LocalDate.now().minusDays(30);
            reportService.salesDaily(LocalDate.now(), to);
            verify(reportQueryMapper).selectSalesDaily(to.minusDays(29), to);
        }
    }

    @Nested
    class Snapshot {

        @Test
        void dateDefaultsToLatestSnapshotDate() {
            when(reportQueryMapper.selectLatestSnapshotDate()).thenReturn(TODAY.minusDays(1));
            reportService.snapshot(null);
            verify(reportQueryMapper).selectSnapshot(TODAY.minusDays(1));
        }

        @Test
        void noSnapshotYetReturnsEmptyListWithoutError() {
            when(reportQueryMapper.selectLatestSnapshotDate()).thenReturn(null);
            assertTrue(reportService.snapshot(null).isEmpty());
        }

        @Test
        void explicitDateBypassesLatestLookup() {
            reportService.snapshot(TODAY);
            verify(reportQueryMapper).selectSnapshot(TODAY);
        }
    }

    @Nested
    class SalesSku {

        @Test
        void clampsLimit() {
            LocalDate today = LocalDate.now();
            LocalDate from = today.minusDays(29);
            reportService.salesSku(from, today, 5000);
            verify(reportQueryMapper).selectSalesSku(from, today, 1000);
            reportService.salesSku(from, today, null);
            verify(reportQueryMapper).selectSalesSku(from, today, 100);
        }
    }

    @Nested
    class Export {

        @Test
        void salesExportProducesValidXlsxWithTwoSheets() throws IOException {
            LocalDate from = LocalDate.now().minusDays(29);
            LocalDate today = LocalDate.now();
            when(reportQueryMapper.selectSalesDaily(from, today)).thenReturn(List.of(
                    new SalesDailyRow(today.minusDays(1), 12, 3),
                    new SalesDailyRow(today, 8, 2)));
            when(reportQueryMapper.selectSalesSku(from, today, 1000)).thenReturn(List.of(
                    new SalesSkuRow(11L, "SKU-001", "商品A", 15),
                    new SalesSkuRow(12L, "SKU-002", null, 5)));

            byte[] bytes = reportService.exportSales(from, today);

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertEquals(2, workbook.getNumberOfSheets());
                Sheet daily = workbook.getSheet("销售日汇总");
                assertEquals(3, daily.getLastRowNum() + 1);
                assertEquals("统计日期", daily.getRow(0).getCell(0).getStringCellValue());
                Sheet sku = workbook.getSheet("SKU明细");
                // 首行=SKU-001(有名称);第二行=SKU-002(名称缺失翻译为空串禁 null,防 POI cell NPE)
                Row secondSku = sku.getRow(2);
                assertEquals("SKU-002", secondSku.getCell(0).getStringCellValue());
                assertEquals("", secondSku.getCell(1).getStringCellValue());
            }
        }

        @Test
        void inventoryExportHandlesEmptySnapshot() throws IOException {
            when(reportQueryMapper.selectLatestSnapshotDate()).thenReturn(null);

            byte[] bytes = reportService.exportInventory(null);
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertEquals(1, workbook.getNumberOfSheets());
                // 仅表头行(快照任务首跑前空态可导出)
                assertEquals(0, workbook.getSheetAt(0).getLastRowNum());
            }
        }

        @Test
        void inventoryExportWritesRows() throws IOException {
            when(reportQueryMapper.selectSnapshot(TODAY)).thenReturn(List.of(
                    new InventorySnapshotRow(TODAY, 11L, "SKU-001", "商品A", 1L, "主仓", 10, 2, 5, 8)));

            byte[] bytes = reportService.exportInventory(TODAY);

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertEquals(1, sheet.getLastRowNum());
                Row row = sheet.getRow(1);
                assertEquals("SKU-001", row.getCell(1).getStringCellValue());
                assertEquals("主仓", row.getCell(3).getStringCellValue());
                assertEquals("8", row.getCell(7).getStringCellValue());
            }
        }
    }

    /** #22 商品分析(四期 BI 首个功能):选项钳制/日期轴逐日对齐/窗口汇总/脏 id 空态 */
    @Nested
    class GoodsAnalytics {

        private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
        private static final LocalDate TO = LocalDate.of(2026, 9, 5);

        @Test
        void optionsClampsLimit() {
            reportService.goodsOptions(null);
            reportService.goodsOptions(5000);
            // null(缺省=上限)与超限值同钳 500
            verify(reportQueryMapper, times(2)).selectSkuOptions(500);
            reportService.goodsOptions(0);
            verify(reportQueryMapper).selectSkuOptions(1);
        }

        @Test
        void trendAlignsDateAxisFillingSalesZeroAndStockNull() {
            // 销量有 9/1~9/2、库存有 9/3 与 9/5(两数据面错位):销量缺日补 0,库存缺日置 null(快照缺失不猜)
            when(reportQueryMapper.selectSkuSalesTrend(11L, FROM, TO)).thenReturn(List.of(
                    new SkuTrendRow(FROM, 3, null),
                    new SkuTrendRow(FROM.plusDays(1), 5, null)));
            when(reportQueryMapper.selectSkuStockTrend(11L, FROM, TO)).thenReturn(List.of(
                    new SkuTrendRow(FROM.plusDays(2), null, 20),
                    new SkuTrendRow(TO, null, 10)));
            when(reportQueryMapper.selectSkuOption(11L)).thenReturn(new SkuOptionRow(11L, "SKU-001", "商品A"));

            SkuTrendResponse resp = reportService.goodsTrend(11L, FROM, TO);

            assertEquals(5, resp.trend().size());
            assertEquals(3, resp.trend().get(0).qtySold());
            assertNull(resp.trend().get(0).qtyOnHand());
            assertEquals(5, resp.trend().get(1).qtySold());
            assertNull(resp.trend().get(1).qtyOnHand());
            assertEquals(0, resp.trend().get(2).qtySold());
            assertEquals(20, resp.trend().get(2).qtyOnHand());
            assertEquals(0, resp.trend().get(3).qtySold());
            assertNull(resp.trend().get(3).qtyOnHand());
            assertEquals(0, resp.trend().get(4).qtySold());
            assertEquals(10, resp.trend().get(4).qtyOnHand());
            assertEquals("SKU-001", resp.sku().skuCode());
        }

        @Test
        void summaryComputesTotalsActiveDaysAndLatestSnapshot() {
            // 同上数据:销量合计 8、动销 2 天、期末库存=窗口内最新非空快照 10@9/5(9/4 缺快照不截断取值链)
            when(reportQueryMapper.selectSkuSalesTrend(11L, FROM, TO)).thenReturn(List.of(
                    new SkuTrendRow(FROM, 3, null),
                    new SkuTrendRow(FROM.plusDays(1), 5, null)));
            when(reportQueryMapper.selectSkuStockTrend(11L, FROM, TO)).thenReturn(List.of(
                    new SkuTrendRow(FROM.plusDays(2), null, 20),
                    new SkuTrendRow(TO, null, 10)));

            SkuTrendResponse resp = reportService.goodsTrend(11L, FROM, TO);

            assertEquals(8, resp.summary().totalQtySold());
            assertEquals(2, resp.summary().activeDays());
            assertEquals(10, resp.summary().latestQtyOnHand());
            assertEquals(TO, resp.summary().latestStockDate());
        }

        @Test
        void unknownSkuReturnsNullSkuWithZeroedTrend() {
            // 脏 id(翻译无行+两数据面空):sku=null 回落,trend 全行 0/null,summary 全零不抛错
            when(reportQueryMapper.selectSkuSalesTrend(99L, FROM, TO)).thenReturn(List.of());
            when(reportQueryMapper.selectSkuStockTrend(99L, FROM, TO)).thenReturn(List.of());
            when(reportQueryMapper.selectSkuOption(99L)).thenReturn(null);

            SkuTrendResponse resp = reportService.goodsTrend(99L, FROM, TO);

            assertNull(resp.sku());
            assertEquals(5, resp.trend().size());
            assertEquals(0, resp.summary().totalQtySold());
            assertEquals(0, resp.summary().activeDays());
            assertNull(resp.summary().latestQtyOnHand());
            assertNull(resp.summary().latestStockDate());
        }
    }
}
