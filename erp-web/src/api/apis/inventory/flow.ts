import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { InventoryFlowResponse, InventoryFlowQuery } from '@/api/interface/inventory/flow'

/**
 * 库存流水(/api/inventory/flows,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const inventoryFlowApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: InventoryFlowQuery & PageQuery) =>
    http.get<PageResult<InventoryFlowResponse>>('/api/inventory/flows', params).then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<InventoryFlowResponse>(`/api/inventory/flows/${id}`),
}