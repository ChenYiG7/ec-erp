/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 库存流水接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 库存流水实体(分页行 / 详情) */
export interface InventoryFlowResponse {
  /** id */
  id: number
  /** 内部SKU */
  skuId: number
  /** 仓库ID */
  warehouseId: number
  /** 流水类型 */
  flowType: string
  /** 变更数量 */
  quantity: number
  /** 变更前 */
  beforeQty: number
  /** 变更后 */
  afterQty: number
  /** 业务类型 */
  bizType: string
  /** 业务单据ID */
  bizId: number
  /** 备注 */
  remark: string
  /** createdBy */
  createdBy: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 库存流水分页查询入参(不含分页参数) */
export interface InventoryFlowQuery {
  /** 内部SKU */
  skuId?: number
  /** 仓库ID */
  warehouseId?: number
  /** 流水类型 */
  flowType?: string
}
