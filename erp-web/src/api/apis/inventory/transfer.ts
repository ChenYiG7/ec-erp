import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { TransferOrderResponse, TransferOrderSaveRequest } from '@/api/interface/inventory/transfer'

/**
 * 调拨单(/api/inventory/transfer-orders,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const transferOrderApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PageQuery) =>
    http
      .get<PageResult<TransferOrderResponse>>('/api/inventory/transfer-orders', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<TransferOrderResponse>(`/api/inventory/transfer-orders/${id}`),
  /** 新增(后端返回主键) */
  create: (data: TransferOrderSaveRequest) => http.post<number>(`/api/inventory/transfer-orders`, data),
  /** 修改 */
  update: (id: number, data: TransferOrderSaveRequest) =>
    http.put<boolean>(`/api/inventory/transfer-orders/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/inventory/transfer-orders/${id}`),
  /** 确认调拨(POST /api/inventory/transfer-orders/{id}/confirm) */
  confirm: (id: number) => http.post<unknown>(`/api/inventory/transfer-orders/${id}/confirm`),
  /** 取消调拨单(POST /api/inventory/transfer-orders/{id}/cancel) */
  cancel: (id: number) => http.post<unknown>(`/api/inventory/transfer-orders/${id}/cancel`),
}
