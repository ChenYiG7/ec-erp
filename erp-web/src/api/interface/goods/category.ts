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
