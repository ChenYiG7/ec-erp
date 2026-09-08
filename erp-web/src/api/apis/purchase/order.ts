import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { PurchaseOrderResponse, PurchaseOrderSaveRequest } from '@/api/interface/purchase/order'

/**
 * 采购单(/api/purchase/orders,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const purchaseOrderApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PageQuery) =>
    http
      .get<PageResult<PurchaseOrderResponse>>('/api/purchase/orders', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<PurchaseOrderResponse>(`/api/purchase/orders/${id}`),
  /** 新增(后端返回主键) */
  create: (data: PurchaseOrderSaveRequest) => http.post<number>(`/api/purchase/orders`, data),
  /** 修改 */
  update: (id: number, data: PurchaseOrderSaveRequest) => http.put<boolean>(`/api/purchase/orders/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/purchase/orders/${id}`),
  /** 关闭采购单(POST /api/purchase/orders/{id}/close) */
  close: (id: number) => http.post<unknown>(`/api/purchase/orders/${id}/close`),
  /** 审核采购单(POST /api/purchase/orders/{id}/audit) */
  audit: (id: number) => http.post<unknown>(`/api/purchase/orders/${id}/audit`),
}
