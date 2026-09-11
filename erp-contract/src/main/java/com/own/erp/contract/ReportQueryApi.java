package com.own.erp.contract;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/10
 * @Description : 报表只读查询契约(#6 Report tools 第八类,查询契约第十件):erp-ai 报表工具取数唯一正道
 *         (铁律 2,禁横向依赖 erp-report),实现收口 erp-api(ReportQueryApiImpl,委托 erp-report ReportService)。
 *         <p>数据面 = order_sales_daily(销量日表)+ inventory_snapshot_daily(库存日快照),两条既有聚合查询,
 *         本契约<b>零新 SQL</b>(§2.3 优先包既有 ReportService 方法);窗口缺省/钳制随域服务
 *         (缺省近 30 天,跨度上限 366 天;快照日缺省=最新快照日,无快照空列表不报错)。
 *         <p>辨析:既有 {@link SalesQueryApi} 是补货/异常工作流吃 order_sales_daily 的契约(按 skuId 集合取数),
 *         与报表中心取数面语义不同,勿混淆复用;利润数经既有 {@link ProfitQueryApi}(#19③)直接消费,
 *         本契约不重复包(避免双契约漂移)。
 *         <p>契约纪律:参数/返回全 record + {@link QueryPage},零 MP 类型、零凭证字段;行视图即 AI 裁剪面
 *         (只留日期/SKU/数量/四量,不含 id 审计列与 raw 字段);只读——@Tool 侧禁写(铁律 7)
 */
public interface ReportQueryApi {

    /**
     * 销售日报分页(按统计日聚合,支付日×SKU 已支付态口径——未绑定 SKU 与未支付订单不统计,docs/03 §7.2)。
     * 窗口缺省近 30 天、跨度上限 366 天(渐归收口在域服务)
     */
    QueryPage<SalesDailyRow> salesDailySummary(ReportSalesQuery query);

    /**
     * SKU 销量排行(窗口内逐 SKU 销量降序;limit 缺省 100、钳制 ≤1000,
     * 工具面另收 ≤20 行防 token 爆炸);名称翻译 join 不滤已删(#7 拍板)
     */
    List<SkuSalesRow> skuSalesTop(ReportSkuQuery query);

    /**
     * 单品逐日趋势(近 days 天含今日):日期轴由域服务按窗口逐日生成对齐——销量缺日补 0(无销售),
     * 库存缺日置 null(快照缺失不猜,#19 禁猜口径;日期轴不进 SQL,#22 先例);
     * 汇总口径(销量合计/动销天数/期末库存)可由点序列推导,不单列(工具面宁少而准)
     */
    List<SkuTrendPoint> skuTrend(Long skuId, Integer days);

    /**
     * 库存快照分页(指定快照日 SKU×仓 四量全行):date 空 = 最新快照日;
     * 快照表为空/指定日无快照返回空页(不报错——快照任务首跑前页面/工具可先见空态)
     */
    QueryPage<InventorySnapshotRow> inventorySnapshotSummary(ReportInvQuery query);

    /**
     * 销售日报过滤 + 分页入参(全 record):pageNo/pageSize 为 int,@Builder 不设时默认 0,
     * 经 page()/size() 归一后生效(AI 侧漏传分页按第 1 页 / 20 条执行)
     */
    @Builder
    record ReportSalesQuery(

            /** 起始日(含,可空=缺省近 30 天) */
            LocalDate dateFrom,

            /** 结束日(含,可空=今天) */
            LocalDate dateTo,

            /** 页码(从 1 起) */
            int pageNo,

            /** 页大小 */
            int pageSize
    ) {

        /** 归一页码(<1 按 1) */
        public int page() {
            return Math.max(pageNo, 1);
        }

        /** 归一页大小(未传/非法按默认 20,上限 100) */
        public int size() {
            return pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        }
    }

    /** 销售日报行(按统计日聚合;order_sales_daily 无店铺/金额列,店铺与金额口径在订单/利润契约面) */
    @Builder
    record SalesDailyRow(

            /** 统计日期(支付日口径) */
            LocalDate statDate,

            /** 当日销量合计(件) */
            long totalQty,

            /** 当日有销量的 SKU 数 */
            long skuCount
    ) {
    }

    /**
     * SKU 销量排行过滤入参(全 record):limit 为 Integer(非 int,防 AI 漏传拆箱 NPE,#6 同类坑),
     * 经 topN() 归一后生效(未传按 20,钳制 ≤1000)
     */
    @Builder
    record ReportSkuQuery(

            /** 起始日(含,可空=缺省近 30 天) */
            LocalDate dateFrom,

            /** 结束日(含,可空=今天) */
            LocalDate dateTo,

            /** 排行行数(可空=缺省 20) */
            Integer limit
    ) {

        /** 归一排行行数(未传/非法按 20,上限 1000 与域服务同源) */
        public int topN() {
            return limit == null || limit < 1 ? 20 : Math.min(limit, 1000);
        }
    }

    /** SKU 销量行(@Builder 防相邻同类型字段错位) */
    @Builder
    record SkuSalesRow(

            /** 内部SKU ID */
            Long skuId,

            /** 内部SKU编码(已删 SKU 历史销量仍回显) */
            String skuCode,

            /** SPU 商品名称(未绑 SPU 为 NULL) */
            String productName,

            /** 窗口内销量合计(件) */
            long totalQty
    ) {
    }

    /**
     * 单品趋势点(@Builder 防 qtySold/qtyOnHand 相邻同类型错位):
     * qtySold 缺日补 0(零动销是合法业务语义),qtyOnHand 缺日 null(快照缺失断点,不猜)
     */
    @Builder
    record SkuTrendPoint(

            /** 日期 */
            LocalDate statDate,

            /** 当日销量(件;缺日=0) */
            Integer qtySold,

            /** 当日库存(跨仓合计;快照缺失=NULL) */
            Integer qtyOnHand
    ) {
    }

    /**
     * 库存快照过滤 + 分页入参:date 可空(空=最新快照日);pageNo/pageSize 为 int,
     * @Builder 不设时默认 0,经 page()/size() 归一后生效(快照全行按 SKU×仓,默认 20 行偏保守,可由入参放大至 100)
     */
    @Builder
    record ReportInvQuery(

            /** 快照日(可空=最新快照日) */
            LocalDate date,

            /** 页码(从 1 起) */
            int pageNo,

            /** 页大小 */
            int pageSize
    ) {

        /** 归一页码(<1 按 1) */
        public int page() {
            return Math.max(pageNo, 1);
        }

        /** 归一页大小(未传/非法按默认 50,上限 100) */
        public int size() {
            return pageSize <= 0 ? 50 : Math.min(pageSize, 100);
        }
    }

    /** 库存快照行(SKU×仓 四量全行;名称翻译 join 不滤已删,历史快照名字仍可读) */
    @Builder
    record InventorySnapshotRow(

            /** 快照日期 */
            LocalDate statDate,

            /** 内部SKU ID */
            Long skuId,

            /** 内部SKU编码 */
            String skuCode,

            /** SPU 商品名称 */
            String productName,

            /** 仓库ID */
            Long warehouseId,

            /** 仓库名称 */
            String whName,

            /** 在库快照 */
            int qtyOnHand,

            /** 占用快照 */
            int qtyLocked,

            /** 在途快照 */
            int qtyTransit,

            /** 可用快照=在库-占用 */
            int qtyAvailable
    ) {
    }
}
