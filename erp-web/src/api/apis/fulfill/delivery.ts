import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { DeliveryOrderResponse, DeliveryOrderQuery, DeliveryOrderSaveRequest } from '@/api/interface/fulfill/delivery'

/**
 * 发货单(/api/fulfill/delivery-orders,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const deliveryOrderApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: DeliveryOrderQuery & PageQuery) =>
    http.get<PageResult<DeliveryOrderResponse>>('/api/fulfill/delivery-orders', params).then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<DeliveryOrderResponse>(`/api/fulfill/delivery-orders/${id}`),
  /** 新增(后端返回主键;仅 WAIT_SHIP+卖家自履约订单可建,#11 建发货单表单槽位) */
  create: (data: DeliveryOrderSaveRequest) => http.post<number>(`/api/fulfill/delivery-orders`, data),
  /** 修改(仅 PENDING;明细整体替换并重算占用:释放旧占→替换→重占新占,#11 后补物流信息槽位) */
  update: (id: number, data: DeliveryOrderSaveRequest) => http.put<unknown>(`/api/fulfill/delivery-orders/${id}`, data),
  /** 确认发货(POST /api/fulfill/delivery-orders/{id}/ship) */
  ship: (id: number) => http.post<unknown>(`/api/fulfill/delivery-orders/${id}/ship`),
  /** 标记签收(POST /api/fulfill/delivery-orders/{id}/deliver) */
  deliver: (id: number) => http.post<unknown>(`/api/fulfill/delivery-orders/${id}/deliver`),
  /** 取消发货单(POST /api/fulfill/delivery-orders/{id}/cancel) */
  cancel: (id: number) => http.post<unknown>(`/api/fulfill/delivery-orders/${id}/cancel`),
}