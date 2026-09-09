import http from '@/utils/request'
import type {
  InventorySnapshotRow,
  ReportDigest,
  ReportWindowQuery,
  SalesDailyRow,
  SalesSkuRow,
  SalesWeeklyRow,
  SkuOptionRow,
  SkuTrendResponse,
} from '@/api/interface/report/report'

/**
 * 报表中心(/api/report,#20 报表域 V1 + #22 商品分析)
 * 聚合查询走 JSON;导出走 blob(拦截器对 Blob 响应原样透传)
 */
export const reportApi = {
  /** 销售日报(窗口缺省近 30 天) */
  salesDaily: (params: ReportWindowQuery) => http.get<SalesDailyRow[]>('/api/report/sales/daily', params),
  /** 销售周报(周一为一周起点) */
  salesWeekly: (params: ReportWindowQuery) => http.get<SalesWeeklyRow[]>('/api/report/sales/weekly', params),
  /** 销售 SKU 明细(销量降序) */
  salesSku: (params: ReportWindowQuery & { limit?: number }) =>
    http.get<SalesSkuRow[]>('/api/report/sales/sku', params),
  /** 库存快照(date 缺省=最新快照日) */
  snapshot: (params: { date?: string }) => http.get<InventorySnapshotRow[]>('/api/report/inventory/snapshot', params),
  /** 销售报表导出(xlsx 双 sheet:日汇总+SKU明细) */
  exportSales: (params: ReportWindowQuery) =>
    http.get<BlobPart>('/api/report/export/sales', params, { responseType: 'blob' }),
  /** 库存快照导出(xlsx 单 sheet;date 缺省=最新快照日) */
  exportInventory: (params: { date?: string }) =>
    http.get<BlobPart>('/api/report/export/inventory', params, { responseType: 'blob' }),
  /** 商品分析 SKU 选项(销量∪快照出现过的 SKU) */
  goodsOptions: (params?: { limit?: number }) => http.get<SkuOptionRow[]>('/api/report/goods/options', params),
  /** 商品分析单 SKU 趋势(销量/库存双序列+窗口汇总) */
  goodsTrend: (params: { skuId: number } & ReportWindowQuery) =>
    http.get<SkuTrendResponse>('/api/report/goods/trend', params),
  /** 经营简报预览(纯读侧零副作用;定时推送由后端 Job 走 #14 出口) */
  digestPreview: (params: { period: ReportDigest['period'] }) =>
    http.get<ReportDigest>('/api/report/digest/preview', params),
}

/** blob 落盘通用下载(文件名由调用方给,后端 Content-Disposition 不跨域透传) */
export function saveBlob(blob: BlobPart, filename: string) {
  const url = URL.createObjectURL(new Blob([blob]))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
