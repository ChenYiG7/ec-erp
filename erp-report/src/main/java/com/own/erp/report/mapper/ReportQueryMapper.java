package com.own.erp.report.mapper;

import com.own.erp.report.report.InventorySnapshotRow;
import com.own.erp.report.report.SalesDailyRow;
import com.own.erp.report.report.SalesSkuRow;
import com.own.erp.report.report.SalesWeeklyRow;
import com.own.erp.report.report.SkuOptionRow;
import com.own.erp.report.report.SkuTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 报表只读查询(#20 报表域 V1):跨域只读聚合 XML(数据源=order_sales_daily/
 *     inventory_snapshot_daily 两个日快照表,join 商品/仓库名称翻译),零写侧零 DDL;
 *     不继承 BaseMapper(纯查询无实体),@MapperScan com.own.erp.**.mapper 天然覆盖;
 *     join 不滤已删(#7 拍板:历史数据名称翻译仍可读)
 */
@Mapper
public interface ReportQueryMapper {

    /** 销售日报:按统计日聚合(窗口含起含止) */
    List<SalesDailyRow> selectSalesDaily(@Param("dateFrom") LocalDate dateFrom,
                                         @Param("dateTo") LocalDate dateTo);

    /** 销售周报:按自然周(周一为起点)聚合 */
    List<SalesWeeklyRow> selectSalesWeekly(@Param("dateFrom") LocalDate dateFrom,
                                           @Param("dateTo") LocalDate dateTo);

    /** 销售 SKU 明细:窗口内逐 SKU 销量降序(LIMIT 由调用方钳制) */
    List<SalesSkuRow> selectSalesSku(@Param("dateFrom") LocalDate dateFrom,
                                     @Param("dateTo") LocalDate dateTo,
                                     @Param("limit") int limit);

    /** 最新快照日(快照表可能为空→NULL) */
    LocalDate selectLatestSnapshotDate();

    /** 库存快照:指定快照日全行(SKU×仓粒度) */
    List<InventorySnapshotRow> selectSnapshot(@Param("date") LocalDate date);

    // ---- 商品分析(#22 四期 BI 首个功能,数据面同两日表,零 DDL 纯读侧) ----

    /** 商品分析 SKU 选项:销量日表 UNION ALL 库存快照出现过的 SKU(名称翻译不滤已删,sku_code 排序,LIMIT 调用方钳制) */
    List<SkuOptionRow> selectSkuOptions(@Param("limit") int limit);

    /** 商品分析单 SKU 翻译行(无行返回 null——脏 id/已不存在,Service 不报错回落) */
    SkuOptionRow selectSkuOption(@Param("skuId") Long skuId);

    /** 商品分析单 SKU 日销量(窗口含起含止;缺日=无行,Service 补 0) */
    List<SkuTrendRow> selectSkuSalesTrend(@Param("skuId") Long skuId,
                                          @Param("dateFrom") LocalDate dateFrom,
                                          @Param("dateTo") LocalDate dateTo);

    /** 商品分析单 SKU 日库存(跨仓 SUM,窗口含起含止;缺日=快照缺失,Service 置 null 断点) */
    List<SkuTrendRow> selectSkuStockTrend(@Param("skuId") Long skuId,
                                          @Param("dateFrom") LocalDate dateFrom,
                                          @Param("dateTo") LocalDate dateTo);
}
