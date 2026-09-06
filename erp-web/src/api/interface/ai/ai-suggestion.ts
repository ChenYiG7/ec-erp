/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** AI建议接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** AI建议实体(分页行 / 详情) */
export interface AiSuggestionResponse {
  /** id */
  id: number
  /** 建议类型 */
  suggestionType: string
  /** 店铺ID */
  shopId: number
  /** 内部SKU */
  skuId: number
  /** refType */
  refType: string
  /** refId */
  refId: number
  /** payloadJson */
  payloadJson: string
  /** 建议摘要 */
  summary: string
  /** 风险等级 */
  riskLevel: string
  /** 状态 */
  status: number
  /** confirmedBy */
  confirmedBy: number
  /** 确认时间 */
  confirmedAt: string
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** AI建议分页查询入参(不含分页参数) */
export interface AiSuggestionQuery {
  /** 店铺ID */
  shopId?: number
  /** 内部SKU */
  skuId?: number
  /** 建议类型 */
  suggestionType?: string
  /** 状态 */
  status?: number
}
