/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 采购单接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 采购单实体(分页行 / 详情) */
export interface PurchaseOrderResponse {
  /** id */
  id: number
  /** 采购单号 */
  poNo: string
  /** 供应商ID */
  supplierId: number
  /** 仓库ID */
  warehouseId: number
  /** 状态 */
  status: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 总金额 */
  totalAmount: string
  /** 备注 */
  remark: string
  /** 创建人 */
  createdBy: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** items */
  items: PurchaseOrderItemResponse[]
}

/** 采购单新增/修改入参 */
export interface PurchaseOrderSaveRequest {
  /** poNo */
  poNo: string
  /** supplierId */
  supplierId: number
  /** warehouseId */
  warehouseId: number
  /** remark */
  remark?: string
  /** createdBy */
  createdBy?: number
  /** items */
  items: PurchaseOrderItemSaveRequest[]
}

/** 嵌套结构(引用自 openapi schema PurchaseOrderItemResponse) */
export interface PurchaseOrderItemResponse {
  /** id */
  id: number
  /** skuId */
  skuId: number
  /** quantity */
  quantity: number
  /** arrivedQty */
  arrivedQty: number
  // 金额(后端 DECIMAL(12,4) 序列化为 string),禁 parseFloat/Number 参与计算(docs/09 §6)
  /** purchasePrice */
  purchasePrice: string
}

/** 嵌套结构(引用自 openapi schema PurchaseOrderItemSaveRequest) */
export interface PurchaseOrderItemSaveRequest {
  /** skuId */
  skuId: number
  /** quantity */
  quantity: number
  // 金额(后端 DECIMAL(12,4) 序列化为 string,选填),禁 parseFloat/Number 参与计算(docs/09 §6)
  /** purchasePrice */
  purchasePrice?: string
}
