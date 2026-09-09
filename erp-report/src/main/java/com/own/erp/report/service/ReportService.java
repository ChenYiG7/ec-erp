package com.own.erp.report.service;

import com.own.erp.report.mapper.ReportQueryMapper;
import com.own.erp.report.report.InventorySnapshotRow;
import com.own.erp.report.report.SalesDailyRow;
import com.own.erp.report.report.SalesSkuRow;
import com.own.erp.report.report.SalesWeeklyRow;
import com.own.erp.report.report.SkuOptionRow;
import com.own.erp.report.report.SkuTrendResponse;
import com.own.erp.report.report.SkuTrendRow;
import com.own.erp.report.report.SkuTrendSummary;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 报表服务(#20 报表域 V1,零 DDL 纯读侧):销量/库存两个日快照数据面的聚合读 + Excel 导出;
 *     #22 商品分析(四期 BI 首个功能)同数据面 SKU 级下钻,并入本服务(窗口逻辑同源)。
 *     窗口拍板:缺省=近 30 天(含起含止),防御上限 366 天(聚合无分页,窗口过大拖库);
 *     快照日缺省=最新快照日(快照表空→空列表,不报错——快照任务首跑前页面可先见空态);
 *     Excel 导出 Apache POI xlsx(仅报表域依赖,页头加粗,文件名 ASCII 防头编码问题)
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 缺省窗口天数(近 30 天) */
    private static final int DEFAULT_WINDOW_DAYS = 30;

    /** 窗口防御上限(天) */
    private static final int MAX_WINDOW_DAYS = 366;

    /** SKU 明细行数缺省/钳制上限 */
    private static final int DEFAULT_SKU_LIMIT = 100;
    private static final int MAX_SKU_LIMIT = 1000;

    /** 商品分析 SKU 选项钳制上限(下拉数据面防御) */
    private static final int MAX_SKU_OPTIONS = 500;

    private final ReportQueryMapper reportQueryMapper;

    /** 销售日报(缺省近 30 天) */
    public List<SalesDailyRow> salesDaily(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate[] window = resolveWindow(dateFrom, dateTo);
        return reportQueryMapper.selectSalesDaily(window[0], window[1]);
    }

    /** 销售周报(同窗口,周一为一周起点) */
    public List<SalesWeeklyRow> salesWeekly(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate[] window = resolveWindow(dateFrom, dateTo);
        return reportQueryMapper.selectSalesWeekly(window[0], window[1]);
    }

    /** 销售 SKU 明细(销量降序,limit 缺省 100 钳制 ≤1000) */
    public List<SalesSkuRow> salesSku(LocalDate dateFrom, LocalDate dateTo, Integer limit) {
        LocalDate[] window = resolveWindow(dateFrom, dateTo);
        int safeLimit = limit == null ? DEFAULT_SKU_LIMIT : Math.max(1, Math.min(limit, MAX_SKU_LIMIT));
        return reportQueryMapper.selectSalesSku(window[0], window[1], safeLimit);
    }

    /** 库存快照(date 缺省=最新快照日;无快照返回空列表不报错) */
    public List<InventorySnapshotRow> snapshot(LocalDate date) {
        LocalDate target = date != null ? date : reportQueryMapper.selectLatestSnapshotDate();
        return target == null ? List.of() : reportQueryMapper.selectSnapshot(target);
    }

    /** 销售报表导出:xlsx 双 sheet(日汇总 + SKU 明细) */
    public byte[] exportSales(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate[] window = resolveWindow(dateFrom, dateTo);
        List<SalesDailyRow> daily = reportQueryMapper.selectSalesDaily(window[0], window[1]);
        List<SalesSkuRow> sku = reportQueryMapper.selectSalesSku(window[0], window[1], MAX_SKU_LIMIT);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            Sheet dailySheet = workbook.createSheet("销售日汇总");
            writeHeader(dailySheet, headerStyle, "统计日期", "销量合计(件)", "有销量SKU数");
            daily.forEach(row -> appendRow(dailySheet, String.valueOf(row.statDate()),
                    String.valueOf(row.totalQty()), String.valueOf(row.skuCount())));
            Sheet skuSheet = workbook.createSheet("SKU明细");
            writeHeader(skuSheet, headerStyle, "SKU编码", "商品名称", "销量合计(件)");
            sku.forEach(row -> appendRow(skuSheet,
                    row.skuCode() == null ? String.valueOf(row.skuId()) : row.skuCode(),
                    row.productName() == null ? "" : row.productName(),
                    String.valueOf(row.totalQty())));
            return toBytes(workbook);
        } catch (IOException e) {
            throw new IllegalStateException("销售报表导出失败", e);
        }
    }

    /** 库存快照导出:xlsx 单 sheet(指定日全行,date 缺省=最新快照日) */
    public byte[] exportInventory(LocalDate date) {
        LocalDate target = date != null ? date : reportQueryMapper.selectLatestSnapshotDate();
        List<InventorySnapshotRow> rows = target == null ? List.of() : reportQueryMapper.selectSnapshot(target);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            Sheet sheet = workbook.createSheet("库存快照");
            writeHeader(sheet, headerStyle, "快照日期", "SKU编码", "商品名称", "仓库",
                    "在库", "占用", "在途", "可用");
            rows.forEach(row -> appendRow(sheet, String.valueOf(row.statDate()),
                    row.skuCode() == null ? String.valueOf(row.skuId()) : row.skuCode(),
                    row.productName() == null ? "" : row.productName(),
                    row.whName() == null ? String.valueOf(row.warehouseId()) : row.whName(),
                    String.valueOf(row.qtyOnHand()), String.valueOf(row.qtyLocked()),
                    String.valueOf(row.qtyTransit()), String.valueOf(row.qtyAvailable())));
            return toBytes(workbook);
        } catch (IOException e) {
            throw new IllegalStateException("库存快照导出失败", e);
        }
    }

    /** 窗口解析(公开给 Controller 生成导出文件名,与服务内查询同口径):缺省近 30 天,跨度钳制 ≤366 天 */
    public LocalDate[] salesWindow(LocalDate dateFrom, LocalDate dateTo) {
        return resolveWindow(dateFrom, dateTo);
    }

    /** 商品分析 SKU 选项(销量∪快照出现过的 SKU,limit 缺省=上限 500 钳制) */
    public List<SkuOptionRow> goodsOptions(Integer limit) {
        int safeLimit = limit == null ? MAX_SKU_OPTIONS : Math.max(1, Math.min(limit, MAX_SKU_OPTIONS));
        return reportQueryMapper.selectSkuOptions(safeLimit);
    }

    /**
     * 商品分析单 SKU 趋势(#22):销量缺日补 0/库存缺日置 null(快照缺失不猜),
     * 日期轴按窗口逐日生成(≤366 行);汇总由趋势行内存计算,期末库存取窗口内最新非空快照
     */
    public SkuTrendResponse goodsTrend(Long skuId, LocalDate dateFrom, LocalDate dateTo) {
        LocalDate[] window = resolveWindow(dateFrom, dateTo);
        Map<LocalDate, Integer> sales = reportQueryMapper.selectSkuSalesTrend(skuId, window[0], window[1]).stream()
                .collect(Collectors.toMap(SkuTrendRow::statDate, r -> r.qtySold() == null ? 0 : r.qtySold()));
        Map<LocalDate, Integer> stock = reportQueryMapper.selectSkuStockTrend(skuId, window[0], window[1]).stream()
                .collect(Collectors.toMap(SkuTrendRow::statDate, r -> r.qtyOnHand() == null ? 0 : r.qtyOnHand()));
        List<SkuTrendRow> trend = new ArrayList<>();
        long totalQty = 0;
        int activeDays = 0;
        Integer latestQtyOnHand = null;
        LocalDate latestStockDate = null;
        for (LocalDate d = window[0]; !d.isAfter(window[1]); d = d.plusDays(1)) {
            int qtySold = sales.getOrDefault(d, 0);
            Integer qtyOnHand = stock.containsKey(d) ? stock.get(d) : null;
            trend.add(new SkuTrendRow(d, qtySold, qtyOnHand));
            totalQty += qtySold;
            if (qtySold > 0) {
                activeDays++;
            }
            if (qtyOnHand != null) {
                latestQtyOnHand = qtyOnHand;
                latestStockDate = d;
            }
        }
        return new SkuTrendResponse(reportQueryMapper.selectSkuOption(skuId), List.copyOf(trend),
                new SkuTrendSummary(totalQty, activeDays, latestQtyOnHand, latestStockDate));
    }

    /** 窗口解析:缺省近 30 天,跨度钳制 ≤366 天(dateFrom 晚于 dateTo 视为非法按缺省回退) */
    private LocalDate[] resolveWindow(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate today = LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : today.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        LocalDate to = dateTo != null ? dateTo : today;
        if (from.isAfter(to)) {
            from = to.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        }
        if (to.toEpochDay() - from.toEpochDay() >= MAX_WINDOW_DAYS) {
            from = to.minusDays(MAX_WINDOW_DAYS - 1L);
        }
        return new LocalDate[]{from, to};
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle style, String... headers) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            sheet.setColumnWidth(i, 16 * 256);
        }
    }

    private void appendRow(Sheet sheet, String... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }
}
