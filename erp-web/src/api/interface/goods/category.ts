/** 商品分类节点(手写,按 tools/openapi.json 快照 CategoryNode 核对;GET /api/goods/categories/tree) */
export interface CategoryNode {
  id: number
  parentId: number
  name: string
  sort: number
  /** 状态:1 启用 / 0 停用 */
  status: number
  children?: CategoryNode[]
}

/** 分类新增/修改入参(手写,按快照 ProductCategorySaveRequest 核对;name 必填,parentId 不传按根处理) */
export interface ProductCategorySaveRequest {
  parentId?: number
  name: string
  sort?: number
  /** 状态:1 启用 / 0 停用 */
  status?: number
}
