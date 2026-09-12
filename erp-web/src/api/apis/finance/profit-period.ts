import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { ProfitPeriodReportResponse, ProfitPeriodReportQuery } from '@/api/interface/finance/profit-period'

/**
 * 周期利润(/api/finance/profit-periods,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const profitPeriodReportApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: ProfitPeriodReportQuery & PageQuery) =>
    http
      .get<PageResult<ProfitPeriodReportResponse>>('/api/finance/profit-periods', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<ProfitPeriodReportResponse>(`/api/finance/profit-periods/${id}`),
}
