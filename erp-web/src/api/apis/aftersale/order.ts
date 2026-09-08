import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type {
  AftersaleOrderResponse,
  AftersaleOrderQuery,
  AftersaleOrderRejectRequest,
  AftersaleOrderRefundRequest,
  AftersaleOrderReceiveReturnRequest,
  AftersaleOrderCompleteRequest,
  AftersaleOrderAgreeRequest,
} from '@/api/interface/aftersale/order'

/**
 * 售后单(/api/aftersale/orders,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const aftersaleOrderApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: AftersaleOrderQuery & PageQuery) =>
    http
      .get<PageResult<AftersaleOrderResponse>>('/api/aftersale/orders', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<AftersaleOrderResponse>(`/api/aftersale/orders/${id}`),
  /** 拒绝售后(POST /api/aftersale/orders/{id}/reject) */
  reject: (id: number, data: AftersaleOrderRejectRequest) =>
    http.post<unknown>(`/api/aftersale/orders/${id}/reject`, data),
  /** 退款(POST /api/aftersale/orders/{id}/refund) */
  refund: (id: number, data: AftersaleOrderRefundRequest) =>
    http.post<unknown>(`/api/aftersale/orders/${id}/refund`, data),
  /** 收退件 + 退货入库(POST /api/aftersale/orders/{id}/receive-return) */
  receiveReturn: (id: number, data: AftersaleOrderReceiveReturnRequest) =>
    http.post<unknown>(`/api/aftersale/orders/${id}/receive-return`, data),
  /** 确认完成(POST /api/aftersale/orders/{id}/complete) */
  complete: (id: number, data: AftersaleOrderCompleteRequest) =>
    http.post<unknown>(`/api/aftersale/orders/${id}/complete`, data),
  /** 同意售后(POST /api/aftersale/orders/{id}/agree) */
  agree: (id: number, data: AftersaleOrderAgreeRequest) =>
    http.post<unknown>(`/api/aftersale/orders/${id}/agree`, data),
}
