import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type {
  FirstLegShipmentResponse,
  FirstLegShipmentSaveRequest,
  FirstLegShipmentQuery,
  FirstLegShipmentShipRequest,
  FirstLegSkuAllocRow,
} from '@/api/interface/finance/firstLeg'

/**
 * 头程发货单(/api/finance/first-leg-shipments,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const firstLegShipmentApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: FirstLegShipmentQuery & PageQuery) =>
    http
      .get<PageResult<FirstLegShipmentResponse>>('/api/finance/first-leg-shipments', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<FirstLegShipmentResponse>(`/api/finance/first-leg-shipments/${id}`),
  /** 新增(后端返回主键) */
  create: (data: FirstLegShipmentSaveRequest) => http.post<number>(`/api/finance/first-leg-shipments`, data),
  /** 修改 */
  update: (id: number, data: FirstLegShipmentSaveRequest) =>
    http.put<boolean>(`/api/finance/first-leg-shipments/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/finance/first-leg-shipments/${id}`),
  /** 确认发货并录运费(POST /api/finance/first-leg-shipments/{id}/ship) */
  ship: (id: number, data: FirstLegShipmentShipRequest) =>
    http.post<unknown>(`/api/finance/first-leg-shipments/${id}/ship`, data),
  /** 关闭头程单(POST /api/finance/first-leg-shipments/{id}/close) */
  close: (id: number) => http.post<unknown>(`/api/finance/first-leg-shipments/${id}/close`),
  /** 取消头程单(POST /api/finance/first-leg-shipments/{id}/cancel) */
  cancel: (id: number) => http.post<unknown>(`/api/finance/first-leg-shipments/${id}/cancel`),
  /** 装箱完成(POST /api/finance/first-leg-shipments/{id}/box) */
  box: (id: number) => http.post<unknown>(`/api/finance/first-leg-shipments/${id}/box`),
  /** 执行运费分摊(POST /api/finance/first-leg-shipments/{id}/allocate) */
  allocate: (id: number) => http.post<unknown>(`/api/finance/first-leg-shipments/${id}/allocate`),
  /** SKU 维度头程费用汇总(Σ ALLOCATED/CLOSED 分摊,利润第三层聚合源;#33 生成器外端点人工补) */
  skuAllocs: (params?: { skuId?: number; shippedFrom?: string; shippedTo?: string }) =>
    http.get<FirstLegSkuAllocRow[]>('/api/finance/first-leg-shipments/sku-allocs', params),
}
