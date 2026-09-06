import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { ShopProductSkuResponse, ShopProductSkuQuery, ShopProductSkuBindRequest } from '@/api/interface/shop/shop-product-sku'

/**
 * SKU匹配(/api/shop-product-skus,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const shopProductSkuApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: ShopProductSkuQuery & PageQuery) =>
    http.get<PageResult<ShopProductSkuResponse>>('/api/shop-product-skus', params).then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<ShopProductSkuResponse>(`/api/shop-product-skus/${id}`),
  /** 人工绑定内部SKU(PUT /api/shop-product-skus/{id}/bind) */
  bind: (id: number, data: ShopProductSkuBindRequest) => http.put<unknown>(`/api/shop-product-skus/${id}/bind`, data),
}