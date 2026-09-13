import type { PageQuery, PageResult } from '@/api/interface'
import type {
  StocktakeOrderCountsRequest,
  StocktakeOrderResponse,
  StocktakeOrderSaveRequest,
} from '@/api/interface/inventory/stocktake'
import http from '@/utils/request'

/**
 * 盘点单(/api/inventory/stocktakes,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const stocktakeOrderApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PageQuery) =>
    http
      .get<PageResult<StocktakeOrderResponse>>('/api/inventory/stocktakes', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<StocktakeOrderResponse>(`/api/inventory/stocktakes/${id}`),
  /** 新增(后端返回主键) */
  create: (data: StocktakeOrderSaveRequest) => http.post<number>(`/api/inventory/stocktakes`, data),
  /** 修改 */
  update: (id: number, data: StocktakeOrderSaveRequest) => http.put<boolean>(`/api/inventory/stocktakes/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/inventory/stocktakes/${id}`),
  /** 开始盘点(POST /api/inventory/stocktakes/{id}/start) */
  start: (id: number) => http.post<unknown>(`/api/inventory/stocktakes/${id}/start`),
  /** 提交盘点(POST /api/inventory/stocktakes/{id}/submit) */
  submit: (id: number) => http.post<unknown>(`/api/inventory/stocktakes/${id}/submit`),
  /** 生成调整(POST /api/inventory/stocktakes/{id}/adjust) */
  adjust: (id: number) => http.post<unknown>(`/api/inventory/stocktakes/${id}/adjust`),
  /** 关闭盘点单(POST /api/inventory/stocktakes/{id}/close) */
  close: (id: number) => http.post<unknown>(`/api/inventory/stocktakes/${id}/close`),
  /** 取消盘点单(POST /api/inventory/stocktakes/{id}/cancel) */
  cancel: (id: number) => http.post<unknown>(`/api/inventory/stocktakes/${id}/cancel`),
  /** 录入实盘(PUT /api/inventory/stocktakes/{id}/counts) */
  counts: (id: number, data: StocktakeOrderCountsRequest) =>
    http.put<unknown>(`/api/inventory/stocktakes/${id}/counts`, data),
}
