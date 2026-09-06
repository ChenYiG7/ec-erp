import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { BrandResponse, BrandSaveRequest } from '@/api/interface/goods/brand'

/**
 * 品牌管理(/api/goods/brands,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const brandApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PageQuery) =>
    http.get<PageResult<BrandResponse>>('/api/goods/brands', params).then(page => ({ list: page.records, total: page.total })),
  /** 新增(后端返回主键) */
  create: (data: BrandSaveRequest) => http.post<number>(`/api/goods/brands`, data),
  /** 修改 */
  update: (id: number, data: BrandSaveRequest) => http.put<boolean>(`/api/goods/brands/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/goods/brands/${id}`),
}