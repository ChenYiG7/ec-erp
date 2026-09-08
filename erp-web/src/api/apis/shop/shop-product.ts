import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { ShopProductResponse, ShopProductQuery } from '@/api/interface/shop/shop-product'

/**
 * 店铺商品(/api/shop-products,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const shopProductApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: ShopProductQuery & PageQuery) =>
    http
      .get<PageResult<ShopProductResponse>>('/api/shop-products', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<ShopProductResponse>(`/api/shop-products/${id}`),
}
