import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { ProductResponse, ProductQuery } from '@/api/interface/goods/product'

/**
 * 商品管理(/api/goods/products,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const productApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: ProductQuery & PageQuery) =>
    http
      .get<PageResult<ProductResponse>>('/api/goods/products', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<ProductResponse>(`/api/goods/products/${id}`),
}
