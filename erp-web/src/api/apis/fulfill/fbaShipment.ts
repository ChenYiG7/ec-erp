import type { PageQuery, PageResult } from '@/api/interface'
import type {
  FbaShipmentQuery,
  FbaShipmentReceiveRequest,
  FbaShipmentResponse,
  FbaShipmentSaveRequest,
} from '@/api/interface/fulfill/fbaShipment'
import http from '@/utils/request'

/**
 * FBA发货单(/api/fulfill/fba-shipments,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const fbaShipmentApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: FbaShipmentQuery & PageQuery) =>
    http
      .get<PageResult<FbaShipmentResponse>>('/api/fulfill/fba-shipments', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<FbaShipmentResponse>(`/api/fulfill/fba-shipments/${id}`),
  /** 新增(后端返回主键) */
  create: (data: FbaShipmentSaveRequest) => http.post<number>(`/api/fulfill/fba-shipments`, data),
  /** 修改 */
  update: (id: number, data: FbaShipmentSaveRequest) => http.put<boolean>(`/api/fulfill/fba-shipments/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/fulfill/fba-shipments/${id}`),
  /** 装箱完成(POST /api/fulfill/fba-shipments/{id}/box) */
  box: (id: number) => http.post<unknown>(`/api/fulfill/fba-shipments/${id}/box`),
  /** 确认发出(POST /api/fulfill/fba-shipments/{id}/ship) */
  ship: (id: number) => http.post<unknown>(`/api/fulfill/fba-shipments/${id}/ship`),
  /** 关闭FBA单(POST /api/fulfill/fba-shipments/{id}/close) */
  close: (id: number) => http.post<unknown>(`/api/fulfill/fba-shipments/${id}/close`),
  /** 取消FBA单(POST /api/fulfill/fba-shipments/{id}/cancel) */
  cancel: (id: number) => http.post<unknown>(`/api/fulfill/fba-shipments/${id}/cancel`),
  /** 收货登记(POST /api/fulfill/fba-shipments/{id}/receive) */
  receive: (id: number, data: FbaShipmentReceiveRequest) =>
    http.post<unknown>(`/api/fulfill/fba-shipments/${id}/receive`, data),
}
