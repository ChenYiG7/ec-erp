/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** FBA发货单接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** FBA发货单实体(分页行 / 详情) */
export interface FbaShipmentResponse {
  /** id */
  id: number
  /** FBA单号 */
  shipmentNo: string
  /** 店铺 */
  shopId: number
  /** shopName */
  shopName: string
  /** 站点 */
  marketplace: string
  /** 发货仓 */
  warehouseId: number
  /** warehouseName */
  warehouseName: string
  /** 平台ShipmentId */
  platformShipmentId: string
  /** 状态 */
  status: string
  /** 发出时间 */
  shippedAt: string
  /** 收货登记 */
  receivedAt: string
  /** 备注 */
  remark: string
  /** createdBy */
  createdBy: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** planItems */
  planItems: FbaPlanItemResponse[]
  /** boxes */
  boxes: FbaBoxResponse[]
  /** diffs */
  diffs: FbaDiffResponse[]
}

/** FBA发货单新增/修改入参 */
export interface FbaShipmentSaveRequest {
  /** shopId */
  shopId: number
  /** marketplace */
  marketplace: string
  /** warehouseId */
  warehouseId: number
  /** platformShipmentId */
  platformShipmentId?: string
  /** remark */
  remark?: string
  /** planItems */
  planItems: FbaPlanItemSave[]
  /** boxes */
  boxes?: FbaBoxSave[]
}

/** FBA发货单动作请求体 */
export interface FbaShipmentReceiveRequest {
  /** items */
  items: FbaReceiveItem[]
}

/** FBA发货单分页查询入参(不含分页参数) */
export interface FbaShipmentQuery {
  /** FBA单号 */
  shipmentNo?: string
  /** 状态 */
  status?: string
  /** 店铺 */
  shopId?: number
  /** 站点 */
  marketplace?: string
  /** 发货仓 */
  warehouseId?: number
}

/** 嵌套结构(引用自 openapi schema FbaPlanItemResponse) */
export interface FbaPlanItemResponse {
  /** id */
  id: number
  /** shipmentId */
  shipmentId: number
  /** skuId */
  skuId: number
  /** skuCode */
  skuCode: string
  /** planQty */
  planQty: number
}

/** 嵌套结构(引用自 openapi schema FbaBoxResponse) */
export interface FbaBoxResponse {
  /** id */
  id: number
  /** shipmentId */
  shipmentId: number
  /** boxNo */
  boxNo: string
  /** weight(kg,可空,装箱记录面) */
  weight?: number
  /** lengthCm(可空) */
  lengthCm?: number
  /** widthCm(可空) */
  widthCm?: number
  /** heightCm(可空) */
  heightCm?: number
  /** createdAt */
  createdAt: string
  /** items */
  items: FbaBoxItemResponse[]
}

/** 嵌套结构(引用自 openapi schema FbaBoxItemResponse) */
export interface FbaBoxItemResponse {
  /** id */
  id: number
  /** boxId */
  boxId: number
  /** skuId */
  skuId: number
  /** skuCode */
  skuCode: string
  /** quantity */
  quantity: number
}

/** 嵌套结构(引用自 openapi schema FbaDiffResponse) */
export interface FbaDiffResponse {
  /** id */
  id: number
  /** shipmentId */
  shipmentId: number
  /** skuId */
  skuId: number
  /** skuCode */
  skuCode: string
  /** shippedQty */
  shippedQty: number
  /** receivedQty */
  receivedQty: number
  /** diffType */
  diffType: string
  /** checkedAt */
  checkedAt: string
}

/** 嵌套结构(引用自 openapi schema FbaPlanItemSave) */
export interface FbaPlanItemSave {
  /** skuId */
  skuId: number
  /** planQty */
  planQty: number
}

/** 嵌套结构(引用自 openapi schema FbaBoxSave) */
export interface FbaBoxSave {
  /** boxNo */
  boxNo: string
  /** weight(kg,可空,装箱记录面) */
  weight?: number
  /** lengthCm(可空) */
  lengthCm?: number
  /** widthCm(可空) */
  widthCm?: number
  /** heightCm(可空) */
  heightCm?: number
  /** items */
  items: FbaBoxItemSave[]
}

/** 嵌套结构(引用自 openapi schema FbaBoxItemSave) */
export interface FbaBoxItemSave {
  /** skuId */
  skuId: number
  /** quantity */
  quantity: number
}

/** 嵌套结构(引用自 openapi schema FbaReceiveItem) */
export interface FbaReceiveItem {
  /** skuId */
  skuId: number
  /** receivedQty */
  receivedQty: number
}
