/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 字典管理接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 字典管理实体(分页行 / 详情) */
export interface SysDictResponse {
  /** id */
  id: number
  /** 字典类型 */
  dictType: string
  /** 字典标签 */
  dictLabel: string
  /** 字典值 */
  dictValue: string
  /** 排序 */
  sort: number
  /** 状态 */
  status: number
  /** 备注 */
  remark: string
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 字典管理新增/修改入参 */
export interface SysDictSaveRequest {
  /** id */
  id?: number
  /** dictType */
  dictType?: string
  /** dictLabel */
  dictLabel?: string
  /** dictValue */
  dictValue?: string
  /** sort */
  sort?: number
  /** status */
  status?: number
  /** remark */
  remark?: string
  /** createdAt */
  createdAt?: string
  /** updatedAt */
  updatedAt?: string
}

/** 字典管理分页查询入参(不含分页参数) */
export interface SysDictQuery {
  /** 字典类型 */
  dictType?: string
}
