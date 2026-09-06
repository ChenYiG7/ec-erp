/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 仓库管理接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 仓库管理实体(分页行 / 详情) */
export interface WarehouseResponse {
  /** id */
  id: number
  /** 仓库名称 */
  whName: string
  /** 仓库类型 */
  whType: string
  /** 国家 */
  country: string
  /** 仓库地址 */
  address: string
  /** 状态 */
  status: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 仓库管理新增/修改入参 */
export interface WarehouseSaveRequest {
  /** whName */
  whName?: string
  /** whType */
  whType?: string
  /** country */
  country?: string
  /** address */
  address?: string
  /** status */
  status?: number
}
