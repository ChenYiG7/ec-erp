/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 头程发货单接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 头程发货单实体(分页行 / 详情) */
export interface FirstLegShipmentResponse {
  /** id */
  id: number
  /** 头程单号 */
  shipmentNo: string
  /** 国内仓 */
  fromWarehouseId: number
  /** 目的仓 */
  toWarehouseId: number
  /** fromWarehouseName */
  fromWarehouseName: string
  /** toWarehouseName */
  toWarehouseName: string
  /** 物流商 */
  carrier: string
  /** 运单号 */
  waybillNo: string
  /** 计费重kg */
  chargeWeight: number
  /** 体积重kg */
  volumeWeight: number
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 运费原币 */
  freightAmount: string
  /** 币种 */
  currency: string
  /** exchangeRate */
  exchangeRate: number
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 运费CNY */
  freightCny: string
  /** 发货时间 */
  shippedAt: string
  /** 分摊策略 */
  allocateStrategy: string
  /** 分摊说明 */
  allocRemark: string
  /** 分摊时间 */
  allocatedAt: string
  /** 状态 */
  status: string
  /** 备注 */
  remark: string
  /** createdBy */
  createdBy: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** boxes */
  boxes: FirstLegBoxResponse[]
  /** allocs */
  allocs: FirstLegAllocResponse[]
}

/** 头程发货单新增/修改入参 */
export interface FirstLegShipmentSaveRequest {
  /** fromWarehouseId */
  fromWarehouseId: number
  /** toWarehouseId */
  toWarehouseId: number
  /** allocateStrategy */
  allocateStrategy?: string
  /** remark */
  remark?: string
  /** boxes */
  boxes?: BoxSave[]
}

/** 头程发货单动作请求体 */
export interface FirstLegShipmentShipRequest {
  /** carrier */
  carrier?: string
  /** waybillNo */
  waybillNo?: string
  /** chargeWeight */
  chargeWeight?: number
  /** volumeWeight */
  volumeWeight?: number
  // 金额(后端 DECIMAL(12,4)),前端 string 直存(docs/09 §6,禁浮点):人工调整生成类型
  /** 运费原币(必填,大于 0;字符串直传,后端 BigDecimal 接收) */
  freightAmount: string
  /** currency */
  currency?: string
  /** exchangeRate */
  exchangeRate?: number
  /** shippedAt */
  shippedAt?: string
}

/** 头程发货单分页查询入参(不含分页参数) */
export interface FirstLegShipmentQuery {
  /** 头程单号 */
  shipmentNo?: string
  /** 状态 */
  status?: string
  /** 国内仓 */
  fromWarehouseId?: number
  /** 目的仓 */
  toWarehouseId?: number
}

/** 嵌套结构(引用自 openapi schema FirstLegBoxResponse) */
export interface FirstLegBoxResponse {
  /** id */
  id: number
  /** shipmentId */
  shipmentId: number
  /** boxNo */
  boxNo: string
  /** weight */
  weight: number
  /** lengthCm */
  lengthCm: number
  /** widthCm */
  widthCm: number
  /** heightCm */
  heightCm: number
  /** items */
  items: FirstLegBoxItemResponse[]
}

/** 嵌套结构(引用自 openapi schema FirstLegBoxItemResponse) */
export interface FirstLegBoxItemResponse {
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

/** 嵌套结构(引用自 openapi schema FirstLegAllocResponse) */
export interface FirstLegAllocResponse {
  /** id */
  id: number
  /** shipmentId */
  shipmentId: number
  /** skuId */
  skuId: number
  /** skuCode */
  skuCode: string
  // 金额 CNY(DECIMAL(12,4)):string 直显(docs/09 §6,人工调整生成类型)
  /** 分摊头程运费(CNY) */
  allocAmount: string
  /** allocBase(件数/克重/金额口径随策略,仅展示) */
  allocBase: number
  /** strategy */
  strategy: string
}

/** SKU 维度头程费用汇总行(GET /sku-allocs,生成器外端点人工补,#33 查询面) */
export interface FirstLegSkuAllocRow {
  /** SKU ID */
  skuId: number
  /** SKU 编码 */
  skuCode: string
  /** 参与分摊的头程单数(ALLOCATED/CLOSED) */
  shipmentCount: number
  // 金额 CNY(DECIMAL(12,4)):string 直显(docs/09 §6)
  /** 头程运费合计(CNY) */
  allocAmountCny: string
}

/** 嵌套结构(引用自 openapi schema BoxSave) */
export interface BoxSave {
  /** boxNo */
  boxNo: string
  // 毛重/外箱尺寸可空(装箱记录面;人工按 schema 可空性调整生成类型)
  /** weight(kg,可空) */
  weight?: number
  /** lengthCm(可空) */
  lengthCm?: number
  /** widthCm(可空) */
  widthCm?: number
  /** heightCm(可空) */
  heightCm?: number
  /** items */
  items: BoxItemSave[]
}

/** 嵌套结构(引用自 openapi schema BoxItemSave) */
export interface BoxItemSave {
  /** skuId */
  skuId: number
  /** quantity */
  quantity: number
}
