/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 库存查询接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 库存查询实体(分页行 / 详情) */
export interface InventoryResponse {
  /** id */
  id: number
  /** 内部SKU */
  skuId: number
  /** 仓库ID */
  warehouseId: number
  /** 在库数量 */
  qtyOnHand: number
  /** 占用数量 */
  qtyLocked: number
  /** 在途数量 */
  qtyTransit: number
  /** 可用数量 */
  qtyAvailable: number
  /** createdAt */
  createdAt: string
  /** 更新时间 */
  updatedAt: string
}

/** 库存查询分页查询入参(不含分页参数) */
export interface InventoryQuery {
  /** 内部SKU */
  skuId?: number
  /** 仓库ID */
  warehouseId?: number
}
