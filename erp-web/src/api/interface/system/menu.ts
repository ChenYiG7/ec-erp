/**
 * 菜单管理接口类型(手写:树形例外页,生成器分页范式不适用)
 * 字段名已按 tools/openapi.json 快照核对(SysMenuResponse / SysMenuSaveRequest),禁手抄漂移
 */

/** 菜单实体(树形;GET /api/system/menus/tree 全量返回) */
export interface SysMenuResponse {
  id: number
  parentId: number
  menuName: string
  /** 菜单类型:1 目录 / 2 菜单 / 3 按钮 */
  menuType: number
  permKey?: string
  path?: string
  component?: string
  icon?: string
  sort: number
  /** 是否显示:1 显示 / 0 隐藏 */
  visible?: number
  /** 状态:1 启用 / 0 停用 */
  status?: number
  children?: SysMenuResponse[]
}

/** 菜单新增/修改入参(必填:menuName/menuType/parentId) */
export interface SysMenuSaveRequest {
  parentId: number
  menuName: string
  menuType: number
  permKey?: string
  path?: string
  component?: string
  icon?: string
  sort?: number
  visible?: number
  status?: number
}

/** 角色菜单授权入参(对齐 openapi RoleMenuAssignRequest;含半选父节点 id) */
export interface RoleMenuAssignRequest {
  menuIds: number[]
}
