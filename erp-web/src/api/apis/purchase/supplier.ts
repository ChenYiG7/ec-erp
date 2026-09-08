import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { SupplierResponse, SupplierSaveRequest } from '@/api/interface/purchase/supplier'

/**
 * 供应商管理(/api/purchase/suppliers,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const supplierApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PageQuery) =>
    http
      .get<PageResult<SupplierResponse>>('/api/purchase/suppliers', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<SupplierResponse>(`/api/purchase/suppliers/${id}`),
  /** 新增(后端返回主键) */
  create: (data: SupplierSaveRequest) => http.post<number>(`/api/purchase/suppliers`, data),
  /** 修改 */
  update: (id: number, data: SupplierSaveRequest) => http.put<boolean>(`/api/purchase/suppliers/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/purchase/suppliers/${id}`),
}
