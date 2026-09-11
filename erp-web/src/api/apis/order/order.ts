import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type {
  ManualOrderSaveRequest,
  ShopOrderQuery,
  ShopOrderResponse,
  ShopOrderReviewCommand,
} from '@/api/interface/order/order'

/**
 * 订单管理(/api/orders,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 * 人工改动(#29 订单域补课):补审核动作与内销录单/改单三个业务动作端点
 */
export const shopOrderApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: ShopOrderQuery & PageQuery) =>
    http
      .get<PageResult<ShopOrderResponse>>('/api/orders', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<ShopOrderResponse>(`/api/orders/${id}`),
  /** 订单审核(POST /api/orders/{id}/review;审核人/时间后端回填,#29) */
  review: (id: number, data: ShopOrderReviewCommand) => http.post<unknown>(`/api/orders/${id}/review`, data),
  /** 内销订单录入(POST /api/orders/manual,返回订单ID;#29) */
  createManual: (data: ManualOrderSaveRequest) => http.post<number>('/api/orders/manual', data),
  /** 修改内销订单(PUT /api/orders/manual/{id};仅 MANUAL + WAIT_SHIP,#29) */
  updateManual: (id: number, data: ManualOrderSaveRequest) => http.put<unknown>(`/api/orders/manual/${id}`, data),
}
