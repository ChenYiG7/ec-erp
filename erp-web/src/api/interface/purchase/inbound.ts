/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 入库单接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 入库单实体(分页行 / 详情) */
export interface PurchaseInboundResponse {
  /** id */
  id: number
  /** 入库单号 */
  inboundNo: string
  /** 采购单ID */
  poId: number
  /** 仓库ID */
  warehouseId: number
  /** 状态 */
  status: string
  /** 备注 */
  remark: string
  /** 创建人 */
  createdBy: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** items */
  items: PurchaseInboundItemResponse[]
}

/** 嵌套结构(引用自 openapi schema PurchaseInboundItemResponse) */
export interface PurchaseInboundItemResponse {
  /** id */
  id: number
  /** poItemId */
  poItemId: number
  /** skuId */
  skuId: number
  /** inboundQty */
  inboundQty: number
}

/** 新增入库单入参(gen:page 从 openapi 快照生成;readonly 域人工扩展,#10 建入库单表单槽位)
 *  status 服务端固定待入库、warehouseId 服务端取采购单收货仓,均不入参 */
export interface PurchaseInboundSaveRequest {
  /** 入库单号(≤64) */
  inboundNo: string
  /** 采购单ID */
  poId: number
  /** 备注 */
  remark?: string
  /** 收货明细(须 ≤ 采购明细剩余未收量,后端预校验) */
  items: PurchaseInboundItemSaveRequest[]
}

/** 收货明细行(po_item 归属校验在后端,sku_id 服务端按采购明细回填) */
export interface PurchaseInboundItemSaveRequest {
  /** 采购明细行ID */
  poItemId: number
  /** 本次入库数量(正数) */
  inboundQty: number
}
