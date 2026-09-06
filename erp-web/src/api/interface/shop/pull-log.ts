/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 拉单日志接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 拉单日志实体(分页行 / 详情) */
export interface PullLogResponse {
  /** id */
  id: number
  /** 店铺ID */
  shopId: number
  /** 数据类型 */
  dataType: string
  /** 窗口起点 */
  windowStart: string
  /** 窗口终点 */
  windowEnd: string
  /** 拉取条数 */
  pulledCount: number
  /** 结果 */
  success: number
  /** 失败原因 */
  errorMsg: string
  /** 耗时(ms) */
  durationMs: number
  /** 触发方式 */
  pullWay: string
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 拉单日志分页查询入参(不含分页参数) */
export interface PullLogQuery {
  /** 店铺ID */
  shopId?: number
  /** 数据类型 */
  dataType?: string
  /** 结果 */
  success?: number
}
