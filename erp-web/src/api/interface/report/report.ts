/**
 * 报表中心接口类型(#20 报表域 V1,对齐后端 ReportController 出参 record)
 * 数据面:order_sales_daily(销量日表)/ inventory_snapshot_daily(库存日快照),零 DDL 纯读侧
 */

/** 销售日报行(GET /api/report/sales/daily;按统计日聚合,支付日口径) */
export interface SalesDailyRow {
  /** 统计日期(yyyy-MM-dd) */
  statDate: string
  /** 销量合计(件) */
  totalQty: number
  /** 有销量 SKU 数 */
  skuCount: number
}

/** 销售周报行(GET /api/report/sales/weekly;周一为一周起点) */
export interface SalesWeeklyRow {
  /** 周起点(周一,yyyy-MM-dd) */
  weekStart: string
  totalQty: number
  skuCount: number
}

/** 销售 SKU 明细行(GET /api/report/sales/sku;窗口内销量降序) */
export interface SalesSkuRow {
  skuId: number
  skuCode: string | null
  productName: string | null
  totalQty: number
}

/** 库存快照行(GET /api/report/inventory/snapshot;SKU×仓 四量) */
export interface InventorySnapshotRow {
  statDate: string
  skuId: number
  skuCode: string | null
  productName: string | null
  warehouseId: number
  whName: string | null
  qtyOnHand: number
  qtyLocked: number
  qtyTransit: number
  qtyAvailable: number
}

/** 报表查询入参(窗口缺省近 30 天,上限 366 天由后端钳制) */
export interface ReportWindowQuery {
  /** yyyy-MM-dd,可空 */
  dateFrom?: string
  /** yyyy-MM-dd,可空 */
  dateTo?: string
}

// ---- 商品分析(#22 四期 BI 首个功能,数据面同两日表,SKU 级下钻) ----

/** 商品分析 SKU 选项行(GET /api/report/goods/options;销量∪快照出现过的 SKU,翻译不滤已删) */
export interface SkuOptionRow {
  skuId: number
  skuCode: string | null
  productName: string | null
}

/** 商品分析单 SKU 日趋势行(GET /api/report/goods/trend;销量缺日=0,库存缺日=null 前端断点不画) */
export interface SkuTrendRow {
  statDate: string
  qtySold: number
  qtyOnHand: number | null
}

/** 商品分析窗口汇总(后端由趋势行内存计算;期末库存=窗口内最新非空快照,无快照双 null) */
export interface SkuTrendSummary {
  totalQtySold: number
  activeDays: number
  latestQtyOnHand: number | null
  latestStockDate: string | null
}

/** 商品分析 SKU 趋势响应(sku 翻译行可为 null——脏 id 回落显示裸 ID) */
export interface SkuTrendResponse {
  sku: SkuOptionRow | null
  trend: SkuTrendRow[]
  summary: SkuTrendSummary
}

// ---- 经营简报(#23 智能报表 V1,窗口由周期语义决定,零 DDL 纯读侧) ----

/** 经营简报(GET /api/report/digest/preview;定时推送走 ReportDigestJob → #14 出口三渠道) */
export interface ReportDigest {
  /** 周期类型 */
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY'
  /** 通知类型(REPORT_DAILY/REPORT_WEEKLY/REPORT_MONTHLY) */
  notifyType: string
  /** 简报标题(推送时即通知标题/邮件主题素材) */
  title: string
  /** 简报正文(纯文本行,站内/邮件/Webhook 三渠道通用) */
  content: string
  /** 窗口起(含,日报=昨日/周报=上周一/月报=上月 1 日) */
  dateFrom: string
  /** 窗口止(含) */
  dateTo: string
}
