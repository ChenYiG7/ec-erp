import http from '@/utils/request'
import type { ProductSkuResponse, SkuOptionResponse } from '@/api/interface/goods/sku'

/**
 * 商品SKU(/api/goods/skus,gen:page 契约外人工登记:#16 SKU 搜索选择器槽位)
 * 契约无全局 SKU 关键字搜索端点,此处仅 SPU 内列表;跨商品搜索收口 SkuSelector 组件(商品 keyword → SPU 内 SKU)
 */
export const goodsSkuApi = {
  /** 查询 SPU 下的 SKU 列表(GET /api/goods/skus?productId=) */
  listByProduct: (productId: number) => http.get<ProductSkuResponse[]>(`/api/goods/skus`, { productId }),
  /** 批量按 ID 查 SKU(#7 专条 2026-09-06 后端补契约):跨页 skuId 列翻译数据源;查无的 ID 不在结果中 */
  batch: (ids: number[]) => http.get<SkuOptionResponse[]>(`/api/goods/skus/batch`, { ids: ids.join(',') }),
}
