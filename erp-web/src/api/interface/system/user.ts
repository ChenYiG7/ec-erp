/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 用户管理接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 用户管理实体(分页行 / 详情) */
export interface SysUserResponse {
  /** id */
  id: number
  /** 用户名 */
  username: string
  /** 昵称 */
  nickname: string
  /** 邮箱 */
  email: string
  /** 手机号 */
  phone: string
  /** 部门ID(sys_dept.id,#27③;null=未分配) */
  deptId: number | null
  /** 状态 */
  status: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 用户管理新增/修改入参 */
export interface SysUserSaveRequest {
  /** username */
  username: string
  /** password */
  password?: string
  /** nickname */
  nickname?: string
  /** email */
  email?: string
  /** phone */
  phone?: string
  /** 部门ID(sys_dept.id,#27③;不传=未分配) */
  deptId?: number
  /** status */
  status?: number
}

/** 用户管理动作请求体 */
export interface SysUserRolesRequest {
  /** roleIds */
  roleIds?: number[]
}

/** 用户-店铺数据授权请求体(#27①;全量重绑,空列表=清空授权) */
export interface SysUserShopsRequest {
  /** shopIds */
  shopIds?: number[]
}

/** 用户管理动作请求体 */
export interface SysUserPasswordRequest {
  /** newPassword */
  newPassword?: string
}

/** 用户管理分页查询入参(不含分页参数) */
export interface SysUserQuery {
  /** 用户名 */
  username?: string
  /** 状态 */
  status?: number
}
