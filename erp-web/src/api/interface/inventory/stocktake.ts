/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 盘点单接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 盘点单实体(分页行 / 详情) */
export interface StocktakeOrderResponse {
  /** id */
  id: number
  /** 盘点单号 */
  stocktakeNo: string
  /** 盘点仓 */
  warehouseId: number
  /** 盘点范围 */
  scopeType: string
  /** 状态 */
  status: string
  /** 备注 */
  remark: string
  /** createdBy */
  createdBy: number
  /** confirmedBy */
  confirmedBy: number
  /** 调整确认时间 */
  confirmedAt: string
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** items */
  items: StocktakeItemResponse[]
}

/** 盘点单新增/修改入参 */
export interface StocktakeOrderSaveRequest {
  /** stocktakeNo */
  stocktakeNo?: string
  /** warehouseId */
  warehouseId?: number
  /** scopeType */
  scopeType?: string
  /** remark */
  remark?: string
  /** skuIds */
  skuIds?: number[]
}

/** 盘点单动作请求体 */
export interface StocktakeOrderCountsRequest {
  /** lines */
  lines?: StocktakeCountLine[]
}

/** 嵌套结构(引用自 openapi schema StocktakeItemResponse) */
export interface StocktakeItemResponse {
  /** id */
  id: number
  /** stocktakeId */
  stocktakeId: number
  /** skuId */
  skuId: number
  /** bookQty */
  bookQty: number
  /** snapshotAt */
  snapshotAt: string
  /** countedQty */
  countedQty: number
  /** diffQty */
  diffQty: number
  /** adjustFlowId */
  adjustFlowId: number
  /** createdAt */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 嵌套结构(引用自 openapi schema StocktakeCountLine) */
export interface StocktakeCountLine {
  /** skuId */
  skuId: number
  /** countedQty */
  countedQty: number
}
