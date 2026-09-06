import http from '@/utils/request'
import type { CategoryNode, ProductCategorySaveRequest } from '@/api/interface/goods/category'

/**
 * 商品分类接口(/api/goods/categories,手写:树形域——契约仅 tree + POST/PUT/DELETE,无分页/详情端点,
 * gen:page 双踩边界(无 page/detail 即 fail + 树形布局人工),CRUD 就地手写,程式对齐生成 api)
 * TODO(#7) 后端成环校验/引用拦截未补:前端编辑态禁改父级、删除挡子节点存在(见分类页)
 */
export const categoryApi = {
  /** 分类全量树(GET /api/goods/categories/tree;非分页,内存组树,sort 升序) */
  tree: () => http.get<CategoryNode[]>('/api/goods/categories/tree'),
  /** 新增分类(parentId 不传按根处理) */
  create: (data: ProductCategorySaveRequest) => http.post<number>('/api/goods/categories', data),
  /** 修改分类(MP updateById 忽略 null 字段,可部分更新) */
  update: (id: number, data: ProductCategorySaveRequest) => http.put<boolean>(`/api/goods/categories/${id}`, data),
  /** 删除分类(一期硬删;TODO(#7) 后有子分类/商品引用时拦截) */
  remove: (id: number) => http.delete<boolean>(`/api/goods/categories/${id}`)
}
