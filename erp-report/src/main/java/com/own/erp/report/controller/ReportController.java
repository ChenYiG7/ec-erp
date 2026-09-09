package com.own.erp.report.controller;

import com.own.erp.common.api.Result;
import com.own.erp.report.report.InventorySnapshotRow;
import com.own.erp.report.report.ReportDigest;
import com.own.erp.report.report.SalesDailyRow;
import com.own.erp.report.report.SalesSkuRow;
import com.own.erp.report.report.SalesWeeklyRow;
import com.own.erp.report.report.SkuOptionRow;
import com.own.erp.report.report.SkuTrendResponse;
import com.own.erp.report.service.ReportDigestService;
import com.own.erp.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 报表中心接口(#20 报表域 V1,四期 BI 起点):销量/库存两个日快照数据面的聚合查询 + Excel 导出,
 *     #22 商品分析 SKU 下钻;#23 经营简报预览(定时推送在 erp-api ReportDigestJob,走 #14 出口三渠道),
 *     只读报表登录即可(经营数据,行级权限随多商户四期);导出 xlsx 流式返回(Content-Disposition 文件名 ASCII,
 *     前端 axios blob 落盘)。AI 工具若需取数再契约化(落位表注记),当前仅前端消费不进 erp-contract
 */
@Tag(name = "报表中心", description = "销售日报/周报/SKU明细与库存快照聚合,Excel 导出(数据面=销量日表+库存日快照)")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportDigestService reportDigestService;

    @Operation(summary = "销售日报", description = "按统计日聚合(支付日口径);窗口缺省近 30 天,上限 366 天")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sales/daily")
    public Result<List<SalesDailyRow>> salesDaily(@RequestParam(required = false) LocalDate dateFrom,
                                                  @RequestParam(required = false) LocalDate dateTo) {
        return Result.ok(reportService.salesDaily(dateFrom, dateTo));
    }

    @Operation(summary = "销售周报", description = "按自然周聚合(周一为一周起点);窗口语义同日报")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sales/weekly")
    public Result<List<SalesWeeklyRow>> salesWeekly(@RequestParam(required = false) LocalDate dateFrom,
                                                    @RequestParam(required = false) LocalDate dateTo) {
        return Result.ok(reportService.salesWeekly(dateFrom, dateTo));
    }

    @Operation(summary = "销售SKU明细", description = "窗口内逐 SKU 销量降序;limit 缺省 100 钳制 ≤1000")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sales/sku")
    public Result<List<SalesSkuRow>> salesSku(@RequestParam(required = false) LocalDate dateFrom,
                                              @RequestParam(required = false) LocalDate dateTo,
                                              @RequestParam(required = false) Integer limit) {
        return Result.ok(reportService.salesSku(dateFrom, dateTo, limit));
    }

    @Operation(summary = "库存快照", description = "指定快照日 SKU×仓 四量全行;date 缺省=最新快照日,无快照返回空列表")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/inventory/snapshot")
    public Result<List<InventorySnapshotRow>> snapshot(@RequestParam(required = false) LocalDate date) {
        return Result.ok(reportService.snapshot(date));
    }

    @Operation(summary = "销售报表导出", description = "xlsx 双 sheet(日汇总+SKU明细);窗口语义同查询")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/export/sales")
    public ResponseEntity<byte[]> exportSales(@RequestParam(required = false) LocalDate dateFrom,
                                              @RequestParam(required = false) LocalDate dateTo) {
        LocalDate[] window = reportService.salesWindow(dateFrom, dateTo);
        return xlsx("sales_" + window[0].toString().replace("-", "") + "_"
                + window[1].toString().replace("-", "") + ".xlsx",
                reportService.exportSales(dateFrom, dateTo));
    }

    @Operation(summary = "商品分析SKU选项", description = "销量日表∪库存快照出现过的 SKU(名称翻译不滤已删);limit 钳制 ≤500")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/goods/options")
    public Result<List<SkuOptionRow>> goodsOptions(@RequestParam(required = false) Integer limit) {
        return Result.ok(reportService.goodsOptions(limit));
    }

    @Operation(summary = "商品分析SKU趋势", description = "单 SKU 逐日销量(缺日=0)/库存(跨仓合计,快照缺失=null 断点)"
            + "双序列 + 窗口汇总(销量合计/动销天数/期末库存);窗口语义同报表查询")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/goods/trend")
    public Result<SkuTrendResponse> goodsTrend(@RequestParam Long skuId,
                                               @RequestParam(required = false) LocalDate dateFrom,
                                               @RequestParam(required = false) LocalDate dateTo) {
        return Result.ok(reportService.goodsTrend(skuId, dateFrom, dateTo));
    }

    @Operation(summary = "库存快照导出", description = "xlsx 单 sheet(指定快照日全行);date 缺省=最新快照日")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/export/inventory")
    public ResponseEntity<byte[]> exportInventory(@RequestParam(required = false) LocalDate date) {
        String suffix = date != null ? date.toString().replace("-", "") : "latest";
        return xlsx("inventory_snapshot_" + suffix + ".xlsx", reportService.exportInventory(date));
    }

    @Operation(summary = "经营简报预览", description = "按周期生成简报文本(日报=昨日单日/周报=上周一至周日/月报=上月),"
            + "纯读侧零副作用;定时推送由 ReportDigestJob(erp.report.digest.* 配置)按 cron 走 #14 出口"
            + "(站内+邮箱+Webhook 三渠道),本端点供前端预览与联调验证")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/digest/preview")
    public Result<ReportDigest> digestPreview(@RequestParam(defaultValue = "DAILY") String period) {
        return Result.ok(reportDigestService.digest(ReportDigestService.Period.parse(period)));
    }

    private ResponseEntity<byte[]> xlsx(String filename, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
