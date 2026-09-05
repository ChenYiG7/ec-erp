import http from '@/utils/request'
import type { CategoryNode } from '@/api/interface/goods/category'

/**
 * 商品分类接口(/api/goods/categories,手写:商品库 TreeFilter 数据源)
 * 分类管理 CRUD 页随后续迭代走 gen:page(届时分类类型迁入生成物,本文件收窄为引用)
 */
export const categoryApi = {
  /** 分类全量树(GET /api/goods/categories/tree;非分页) */
  tree: () => http.get<CategoryNode[]>('/api/goods/categories/tree'),
}
