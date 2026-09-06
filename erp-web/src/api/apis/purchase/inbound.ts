import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { PurchaseInboundResponse, PurchaseInboundSaveRequest } from '@/api/interface/purchase/inbound'

/**
 * 入库单(/api/purchase/inbounds,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const purchaseInboundApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PageQuery) =>
    http.get<PageResult<PurchaseInboundResponse>>('/api/purchase/inbounds', params).then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<PurchaseInboundResponse>(`/api/purchase/inbounds/${id}`),
  /** 新增(后端返回主键;status 服务端固定待入库,入库仓取采购单收货仓,#10 建单表单槽位) */
  create: (data: PurchaseInboundSaveRequest) => http.post<number>(`/api/purchase/inbounds`, data),
  /** 确认入库(POST /api/purchase/inbounds/{id}/confirm) */
  confirm: (id: number) => http.post<unknown>(`/api/purchase/inbounds/${id}/confirm`),
  /** 取消入库单(POST /api/purchase/inbounds/{id}/cancel) */
  cancel: (id: number) => http.post<unknown>(`/api/purchase/inbounds/${id}/cancel`),
}