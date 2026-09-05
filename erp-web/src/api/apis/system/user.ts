import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { SysUserResponse, SysUserSaveRequest, SysUserQuery, SysUserRolesRequest, SysUserPasswordRequest } from '@/api/interface/system/user'

/**
 * 用户接口(/api/system/users)
 * sysUserApi 主体由 pnpm gen:page 按 openapi 快照生成(见 tools/specs/system-user.txt),
 * changePassword 为 P2 手写遗留并入;分页差异只在 page 内单点收口
 */
export const sysUserApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: SysUserQuery & PageQuery) =>
    http.get<PageResult<SysUserResponse>>('/api/system/users', params).then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<SysUserResponse>(`/api/system/users/${id}`),
  /** 新增(后端返回主键) */
  create: (data: SysUserSaveRequest) => http.post<number>(`/api/system/users`, data),
  /** 修改 */
  update: (id: number, data: SysUserSaveRequest) => http.put<boolean>(`/api/system/users/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/system/users/${id}`),
  /** 用户已分配角色 id 列表(GET /api/system/users/{id}/roles) */
  getRoles: (id: number) => http.get<number[]>(`/api/system/users/${id}/roles`),
  /** 保存用户角色分配(PUT /api/system/users/{id}/roles) */
  putRoles: (id: number, data: SysUserRolesRequest) => http.put<void>(`/api/system/users/${id}/roles`, data),
  /** 管理员重置密码(PUT /api/system/users/{id}/password) */
  password: (id: number, data: SysUserPasswordRequest) => http.put<void>(`/api/system/users/${id}/password`, data),
}

/** 本人改密(校验原密码后覆盖;改密成功需重新登录;Header PasswordDialog 在用) */
export const UserApi = {
  changePassword: (params: { oldPassword: string; newPassword: string }) =>
    http.put<void>('/api/system/users/password', params)
}
