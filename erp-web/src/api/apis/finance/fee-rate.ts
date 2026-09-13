import type { PageQuery, PageResult } from '@/api/interface'
import type {
  PlatformFeeRateQuery,
  PlatformFeeRateResponse,
  PlatformFeeRateSaveRequest,
} from '@/api/interface/finance/fee-rate'
import http from '@/utils/request'

/**
 * 平台费率(/api/finance/fee-rates,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const platformFeeRateApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PlatformFeeRateQuery & PageQuery) =>
    http
      .get<PageResult<PlatformFeeRateResponse>>('/api/finance/fee-rates', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<PlatformFeeRateResponse>(`/api/finance/fee-rates/${id}`),
  /** 新增(后端返回主键) */
  create: (data: PlatformFeeRateSaveRequest) => http.post<number>(`/api/finance/fee-rates`, data),
  /** 修改 */
  update: (id: number, data: PlatformFeeRateSaveRequest) => http.put<boolean>(`/api/finance/fee-rates/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/finance/fee-rates/${id}`),
}
