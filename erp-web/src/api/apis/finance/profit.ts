import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { OrderProfitQuery, OrderProfitRow, OrderProfitSummary } from '@/api/interface/finance/profit'

/**
 * 实时销售利润(/api/finance/profit,#19③ 三口径第一层)
 * 后端 QueryPage{list,total} 序列化形态(契约 record,非 MP Page——与分页域差异在此单点收口)
 */
export const profitApi = {
  /** 订单行利润分页(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: OrderProfitQuery & PageQuery) =>
    http.get<PageResult<OrderProfitRow>>('/api/finance/profit', params).then(page => ({ list: page.records, total: page.total })),
  /** 同条件汇总(缺口单独计数) */
  summary: (params: OrderProfitQuery) => http.get<OrderProfitSummary>('/api/finance/profit/summary', params),
}
