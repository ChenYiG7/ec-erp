import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { WarehouseResponse, WarehouseSaveRequest } from '@/api/interface/warehouse/warehouse'

/**
 * 仓库管理(/api/warehouse/warehouses,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const warehouseApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PageQuery) =>
    http
      .get<PageResult<WarehouseResponse>>('/api/warehouse/warehouses', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<WarehouseResponse>(`/api/warehouse/warehouses/${id}`),
  /** 新增(后端返回主键) */
  create: (data: WarehouseSaveRequest) => http.post<number>(`/api/warehouse/warehouses`, data),
  /** 修改 */
  update: (id: number, data: WarehouseSaveRequest) => http.put<boolean>(`/api/warehouse/warehouses/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/warehouse/warehouses/${id}`),
}
