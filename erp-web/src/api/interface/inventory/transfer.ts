/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 调拨单接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 调拨单实体(分页行 / 详情) */
export interface TransferOrderResponse {
  /** id */
  id: number
  /** 调拨单号 */
  transferNo: string
  /** 调出仓 */
  fromWarehouseId: number
  /** 调入仓 */
  toWarehouseId: number
  /** 状态:DRAFT草稿/IN_TRANSIT在途(已发未达)/CONFIRMED已确认/CANCELED已取消 */
  status: string
  /** 动账模式(#30):DIRECT确认即达/IN_TRANSIT在途(OUT→到货IN) */
  transitMode: string
  /** 备注 */
  remark: string
  /** createdBy */
  createdBy: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** items */
  items: TransferOrderItemResponse[]
}

/** 调拨单新增/修改入参 */
export interface TransferOrderSaveRequest {
  /** transferNo */
  transferNo?: string
  /** fromWarehouseId */
  fromWarehouseId?: number
  /** toWarehouseId */
  toWarehouseId?: number
  /** 动账模式(#30):DIRECT确认即达(默认)/IN_TRANSIT在途(OUT→到货IN) */
  transitMode?: string
  /** remark */
  remark?: string
  /** items */
  items?: TransferOrderItemSaveRequest[]
}

/** 嵌套结构(引用自 openapi schema TransferOrderItemResponse) */
export interface TransferOrderItemResponse {
  /** id */
  id: number
  /** transferId */
  transferId: number
  /** skuId */
  skuId: number
  /** quantity */
  quantity: number
  /** createdAt */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 嵌套结构(引用自 openapi schema TransferOrderItemSaveRequest) */
export interface TransferOrderItemSaveRequest {
  /** skuId */
  skuId: number
  /** quantity */
  quantity: number
}
