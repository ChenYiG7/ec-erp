import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { ShopOrderResponse, ShopOrderQuery } from '@/api/interface/order/order'

/**
 * 订单管理(/api/orders,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const shopOrderApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: ShopOrderQuery & PageQuery) =>
    http
      .get<PageResult<ShopOrderResponse>>('/api/orders', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<ShopOrderResponse>(`/api/orders/${id}`),
}
