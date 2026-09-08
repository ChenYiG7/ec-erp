import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { SysRoleResponse, SysRoleSaveRequest } from '@/api/interface/system/role'

/**
 * 角色管理(/api/system/roles,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const sysRoleApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PageQuery) =>
    http
      .get<PageResult<SysRoleResponse>>('/api/system/roles', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 新增(后端返回主键) */
  create: (data: SysRoleSaveRequest) => http.post<number>(`/api/system/roles`, data),
  /** 修改 */
  update: (id: number, data: SysRoleSaveRequest) => http.put<boolean>(`/api/system/roles/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/system/roles/${id}`),
}
