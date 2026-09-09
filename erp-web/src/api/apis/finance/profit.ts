import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type {
  OrderProfitQuery,
  OrderProfitRow,
  OrderProfitSummary,
  ProfitDailyTrendRow,
  ProfitSkuRankRow,
} from '@/api/interface/finance/profit'

/**
 * 实时销售利润(/api/finance/profit,#19③ 三口径第一层)
 * 后端 QueryPage{list,total} 序列化形态(契约 record,非 MP Page——与分页域差异在此单点收口)
 */
export const profitApi = {
  /** 订单行利润分页(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: OrderProfitQuery & PageQuery) =>
    http
      .get<PageResult<OrderProfitRow>>('/api/finance/profit', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 同条件汇总(缺口单独计数) */
  summary: (params: OrderProfitQuery) => http.get<OrderProfitSummary>('/api/finance/profit/summary', params),
  /** 利润日趋势(#21 利润看板,按下单日聚合) */
  trend: (params: OrderProfitQuery) => http.get<ProfitDailyTrendRow[]>('/api/finance/profit/trend', params),
  /** SKU 利润排行(#21 利润看板,利润降序) */
  skuRank: (params: OrderProfitQuery & { topN?: number }) =>
    http.get<ProfitSkuRankRow[]>('/api/finance/profit/sku-rank', params),
}
