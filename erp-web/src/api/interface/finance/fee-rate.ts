/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 平台费率接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 平台费率实体(分页行 / 详情) */
export interface PlatformFeeRateResponse {
  /** id */
  id: number
  /** 平台 */
  platform: string
  /** 费种 */
  feeType: string
  /** marketplace */
  marketplace: string
  /** categoryPath */
  categoryPath: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 费率 */
  rate: string
  /** 生效起 */
  effFrom: string
  /** 生效止(空=长期) */
  effTo: string
  /** 来源 */
  source: string
  /** 备注 */
  remark: string
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 平台费率新增/修改入参 */
export interface PlatformFeeRateSaveRequest {
  /** platform */
  platform: string
  /** feeType */
  feeType: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** rate */
  rate: string
  /** effFrom */
  effFrom: string
  /** effTo */
  effTo?: string
  /** remark */
  remark?: string
}

/** 平台费率分页查询入参(不含分页参数) */
export interface PlatformFeeRateQuery {
  /** 平台 */
  platform?: string
  /** 费种 */
  feeType?: string
}
