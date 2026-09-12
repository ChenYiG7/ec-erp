/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 操作日志接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 操作日志实体(分页行 / 详情) */
export interface SysOperLogResponse {
  /** id */
  id: number
  /** userId */
  userId: number
  /** 操作人 */
  username: string
  /** 模块 */
  module: string
  /** 动作 */
  action: string
  /** 业务类型 */
  bizType: string
  /** 业务ID */
  bizId: number
  /** 参数 */
  paramsJson: string
  /** 结果 */
  resultStatus: string
  /** 失败原因 */
  errorMsg: string
  /** IP */
  ip: string
  /** 链路ID */
  traceId: string
  /** 耗时(ms) */
  costMs: number
  /** 操作时间 */
  createdAt: string
}

/** 操作日志分页查询入参(不含分页参数) */
export interface SysOperLogQuery {
  /** 操作人 */
  username?: string
  /** 模块 */
  module?: string
  /** 结果 */
  resultStatus?: string
  /** beginTime */
  beginTime?: string
  /** endTime */
  endTime?: string
}
