package com.own.erp.ai.tools;

import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.contract.ProfitQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.ReportQueryApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/10
 * @Description : 报表查询工具(#6 Report tools,启航 11 类 checklist 第八类,报表数据面已就绪):
 *         报表面走 {@link ReportQueryApi} 契约(#6 查询契约第十件,委托 erp-report)、利润面走既有
 *         {@link ProfitQueryApi}(#19③)——<b>利润不重复包进报表契约</b>(避免双契约漂移)。
 *         <p>只读铁律(铁律 7):方法全部只读契约调用,无任何写路径,产出不落 ai_suggestion(纯查数无建议闭环);
 *         SQL 不经模型拼装(参数即工具入参);契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)。
 *         <p>工具面裁剪(计划书 §2.2):单次返回<b>硬上限 20 行</b>(表格类分页/排行统一钳到 20,
 *         趋势点上限 30 天)防 token 爆炸;字段面即契约行视图(日期/SKU/数量/四量/金额),内部审计列与 raw 字段不进契约。
 *         <p>未开放锚点:ACOS/广告报表工具随 #20 广告 API 真凭证落地后按第九类扩容(ad_report_daily 草案已备)。
 */
@Component
public class ReportTools {

    /** 表格类返回硬上限(行):分页 pageSize 与 SKU 排行 limit 统一钳制,防 token 爆炸(计划书 §2.2) */
    private static final int MAX_TOOL_ROWS = 20;

    /** 单品趋势窗口缺省/上限(天):自然月窗口,点行仅 3 字段极轻 */
    private static final int DEFAULT_TREND_DAYS = 30;
    private static final int MAX_TREND_DAYS = 30;

    private final ReportQueryApi reportQueryApi;
    private final ProfitQueryApi profitQueryApi;

    public ReportTools(@Lazy ReportQueryApi reportQueryApi, @Lazy ProfitQueryApi profitQueryApi) {
        this.reportQueryApi = reportQueryApi;
        this.profitQueryApi = profitQueryApi;
    }

    @Tool(description = "查询销售日报:按统计日聚合的当日销量与有销量SKU数。口径=支付日×SKU,仅统计已支付态"
            + "(待发货/已发货/已完成)且已绑定内部SKU的订单行;日期区间缺省近30天,跨度上限366天。"
            + "问「最近N天销量」用本工具")
    public QueryPage<ReportQueryApi.SalesDailyRow> reportSalesDaily(
            @ToolParam(required = false, description = "起始日期 yyyy-MM-dd(含),不传默认30天前") LocalDate dateFrom,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd(含),不传默认今天") LocalDate dateTo,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大20") Integer pageSize) {
        return reportQueryApi.salesDailySummary(ReportQueryApi.ReportSalesQuery.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(toolPageSize(pageSize))
                .build());
    }

    @Tool(description = "查询SKU销量排行:日期区间内逐SKU销量降序(含内部SKU编码与商品名)。"
            + "问「哪些SKU卖得最好/top5」用本工具;日期缺省近30天,返回行数上限20")
    public List<ReportQueryApi.SkuSalesRow> reportSkuSalesTop(
            @ToolParam(required = false, description = "起始日期 yyyy-MM-dd(含),不传默认30天前") LocalDate dateFrom,
            @ToolParam(required = false, description = "结束日期 yyyy-MM-dd(含),不传默认今天") LocalDate dateTo,
            @ToolParam(required = false, description = "返回前N名,不传默认20,最大20") Integer limit) {
        return reportQueryApi.skuSalesTop(ReportQueryApi.ReportSkuQuery.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .limit(toolRowLimit(limit))
                .build());
    }

    @Tool(description = "查询单个SKU的逐日销量与库存趋势(近N天,含今日):销量缺日记0,库存缺日记null(快照缺失,"
            + "不是0;求和时跳过null)。销量合计/动销天数/期末库存可由返回点序列自行推导。"
            + "入参 skuId 从SKU销量排行或SKU编码查询工具获取")
    public List<ReportQueryApi.SkuTrendPoint> reportSkuTrend(
            @ToolParam(description = "内部SKU ID(product_sku.id)") Long skuId,
            @ToolParam(required = false, description = "回溯天数(含今日),不传默认30,最大30") Integer days) {
        return reportQueryApi.skuTrend(skuId, toolTrendDays(days));
    }

    @Tool(description = "查询库存快照:指定快照日的SKU×仓库四量(在库/占用/在途/可用)。"
            + "快照为每日存量的历史留痕;日期不传=最新快照日,该日无快照返回空列表。"
            + "问「某天库存多少」用本工具;问「当前实时库存」用库存查询工具")
    public QueryPage<ReportQueryApi.InventorySnapshotRow> reportInventorySnapshot(
            @ToolParam(required = false, description = "快照日期 yyyy-MM-dd,不传默认最新快照日") LocalDate date,
            @ToolParam(required = false, description = "页码,从1起,不传默认1") Integer pageNo,
            @ToolParam(required = false, description = "页大小,不传默认20,最大20") Integer pageSize) {
        return reportQueryApi.inventorySnapshotSummary(ReportQueryApi.ReportInvQuery.builder()
                .date(date)
                .pageNo(pageNo == null ? 0 : pageNo)
                .pageSize(toolPageSize(pageSize))
                .build());
    }

    @Tool(description = "查询实时销售利润汇总(单位CNY):利润=售价−出库成本−平台佣金,口径=订单行粒度、"
            + "已支付态三态、按下单时间过滤;成本按移动加权、佣金按平台结算归集。缺汇率/未出库/待结算的订单行"
            + "不计入对应金额但单独计数(不静默归零),数字偏低时先看缺口计数。时间区间按当日含首含尾")
    public OrderProfitSummary reportProfitSummary(
            @ToolParam(required = false, description = "店铺ID,精确过滤,不传=全店铺") Long shopId,
            @ToolParam(required = false, description = "平台枚举名:TAOBAO/AMAZON等,不传=全平台") String platform,
            @ToolParam(required = false, description = "内部SKU ID,不传=全SKU") Long skuId,
            @ToolParam(required = false, description = "下单起始日 yyyy-MM-dd(含),不传=不限") LocalDate dateFrom,
            @ToolParam(required = false, description = "下单结束日 yyyy-MM-dd(含当日),不传=不限") LocalDate dateTo) {
        return profitQueryApi.summarize(new OrderProfitQuery(shopId, platform, skuId,
                dateFrom == null ? null : dateFrom.atStartOfDay(),
                // 契约 dateTo 语义为「不含」,工具面对模型暴露「含首含尾」的日期语义,此处 +1 天补齐
                dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(),
                null, null));
    }

    /** 归一表格类页大小:未传按上限 20,超出钳到 20(工具面硬上限,契约侧另有 ≤100 兜底) */
    private static int toolPageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? MAX_TOOL_ROWS : Math.min(pageSize, MAX_TOOL_ROWS);
    }

    /** 归一排行行数:未传按上限 20,超出钳到 20 */
    private static int toolRowLimit(Integer limit) {
        return limit == null || limit < 1 ? MAX_TOOL_ROWS : Math.min(limit, MAX_TOOL_ROWS);
    }

    /** 归一趋势天数:未传按 30,钳制 1..30 */
    private static int toolTrendDays(Integer days) {
        return days == null || days < 1 ? DEFAULT_TREND_DAYS : Math.min(days, MAX_TREND_DAYS);
    }
}
