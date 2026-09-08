import http from '@/utils/request'
import type { SysMenuResponse, SysMenuSaveRequest, RoleMenuAssignRequest } from '@/api/interface/system/menu'

/**
 * 菜单接口(/api/system/menus,手写:树形例外页,无分页无详情)
 * 角色授权两件(GET/PUT /roles/{roleId})与 RoleMenuDialog 配套
 */
export const sysMenuApi = {
  /** 菜单全量树(GET /api/system/menus/tree;非分页) */
  tree: () => http.get<SysMenuResponse[]>('/api/system/menus/tree'),
  /** 新增菜单(后端返回主键) */
  create: (data: SysMenuSaveRequest) => http.post<number>('/api/system/menus', data),
  /** 修改菜单 */
  update: (id: number, data: SysMenuSaveRequest) => http.put<boolean>(`/api/system/menus/${id}`, data),
  /** 删除菜单 */
  remove: (id: number) => http.delete<boolean>(`/api/system/menus/${id}`),
  /** 角色已授权菜单 id 列表(GET /api/system/menus/roles/{roleId}) */
  getRoleMenuIds: (roleId: number) => http.get<number[]>(`/api/system/menus/roles/${roleId}`),
  /** 保存角色菜单授权(PUT /api/system/menus/roles/{roleId};需含半选父 id) */
  assignRoleMenus: (roleId: number, data: RoleMenuAssignRequest) =>
    http.put<void>(`/api/system/menus/roles/${roleId}`, data),
}
