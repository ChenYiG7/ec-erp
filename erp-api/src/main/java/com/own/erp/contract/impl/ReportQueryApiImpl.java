package com.own.erp.contract.impl;

import com.own.erp.contract.QueryPage;
import com.own.erp.contract.ReportQueryApi;
import com.own.erp.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/10
 * @Description : ReportQueryApi 实现(#6 Report tools 第八类,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 报表工具取数委托 erp-report ReportService(报表域只读聚合的唯一出口),
 *         域 record → 契约 record 显式逐字段映射(禁反射拷贝,漏字段编译期可见)。
 *         <p>零新 SQL(§2.3 优先包既有 ReportService 方法):分页为<b>内存分页</b>——数据面行数天然受限
 *         (日报 ≤365 行 / 单日快照全行),为 LIMIT 新写 SQL 不值得;
 *         窗口缺省/钳制(近 30 天、≤366 天)与快照日缺省(最新日)全部沿用域服务口径,此处不重复实现。
 *         <p>注入不加 @Lazy:ReportService 不反向注入契约(无构造环),与 18 个同目录 Impl 惯例一致
 *         数据权限(#27①):销售日报/快照面为全店聚合无店铺列,显式不注入(店铺轴报告随需求另立)
 */
@Component
@RequiredArgsConstructor
public class ReportQueryApiImpl implements ReportQueryApi {

    /** 趋势窗口缺省天数(与报表域 DEFAULT_WINDOW_DAYS 同源语义:近 30 天) */
    private static final int DEFAULT_TREND_DAYS = 30;

    /** 趋势窗口上限天数(与报表域 MAX_WINDOW_DAYS 同源的防御上限) */
    private static final int MAX_TREND_DAYS = 366;

    private final ReportService reportService;

    @Override
    public QueryPage<SalesDailyRow> salesDailySummary(ReportSalesQuery query) {
        // erp-report 同名 record 需全限定:本类经 implements 继承契约嵌套类型(简单名指向契约 record)
        List<com.own.erp.report.report.SalesDailyRow> rows =
                reportService.salesDaily(query.dateFrom(), query.dateTo());
        List<SalesDailyRow> views = rows.stream()
                .map(r -> SalesDailyRow.builder()
                        .statDate(r.statDate())
                        .totalQty(r.totalQty())
                        .skuCount(r.skuCount())
                        .build())
                .toList();
        return paged(views, query.page(), query.size());
    }

    @Override
    public List<SkuSalesRow> skuSalesTop(ReportSkuQuery query) {
        return reportService.salesSku(query.dateFrom(), query.dateTo(), query.topN()).stream()
                .map(r -> SkuSalesRow.builder()
                        .skuId(r.skuId())
                        .skuCode(r.skuCode())
                        .productName(r.productName())
                        .totalQty(r.totalQty())
                        .build())
                .toList();
    }

    /**
     * 单品趋势:窗口 = 近 days 天(含今日),由本层折算为 [from,to] 交给域服务逐日对齐
     * (日期轴不进 SQL);skuId 空返回空列表(契约不抛异常,由工具描述约束调用方)
     */
    @Override
    public List<SkuTrendPoint> skuTrend(Long skuId, Integer days) {
        if (skuId == null) {
            return List.of();
        }
        int safeDays = days == null || days < 1 ? DEFAULT_TREND_DAYS : Math.min(days, MAX_TREND_DAYS);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(safeDays - 1L);
        return reportService.goodsTrend(skuId, from, to).trend().stream()
                .map(r -> SkuTrendPoint.builder()
                        .statDate(r.statDate())
                        .qtySold(r.qtySold())
                        .qtyOnHand(r.qtyOnHand())
                        .build())
                .toList();
    }

    @Override
    public QueryPage<InventorySnapshotRow> inventorySnapshotSummary(ReportInvQuery query) {
        // date 空 → 域服务取最新快照日;快照表空 → 空列表(域服务已兜,此处透传)
        List<com.own.erp.report.report.InventorySnapshotRow> rows = reportService.snapshot(query.date());
        List<InventorySnapshotRow> views = rows.stream()
                .map(r -> InventorySnapshotRow.builder()
                        .statDate(r.statDate())
                        .skuId(r.skuId())
                        .skuCode(r.skuCode())
                        .productName(r.productName())
                        .warehouseId(r.warehouseId())
                        .whName(r.whName())
                        .qtyOnHand(r.qtyOnHand())
                        .qtyLocked(r.qtyLocked())
                        .qtyTransit(r.qtyTransit())
                        .qtyAvailable(r.qtyAvailable())
                        .build())
                .toList();
        return paged(views, query.page(), query.size());
    }

    /**
     * 内存分页(零新 SQL):list 不超窗口/快照日上限,subList 越界钳到尾部;
     * total 恒为满足条件的全量行数(非本页行数,与 QueryPage 语义一致)
     */
    private static <T> QueryPage<T> paged(List<T> all, int page, int size) {
        int total = all.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        return QueryPage.of(List.copyOf(all.subList(from, to)), total);
    }
}
