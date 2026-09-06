/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 发货单接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 发货单实体(分页行 / 详情) */
export interface DeliveryOrderResponse {
  /** id */
  id: number
  /** 发货单号 */
  deliveryNo: string
  /** 订单ID */
  orderId: number
  /** 店铺ID */
  shopId: number
  /** 仓库ID */
  warehouseId: number
  /** 类型 */
  type: string
  /** 状态 */
  status: string
  /** 承诺发货时限 */
  shipByTime: string
  /** 物流公司 */
  logisticsCompany: string
  /** 运单号 */
  trackingNo: string
  /** waybillUrl */
  waybillUrl: string
  /** 发货时间 */
  shippedAt: string
  /** createdBy */
  createdBy: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** items */
  items: DeliveryOrderItemResponse[]
}

/** 发货单分页查询入参(不含分页参数) */
export interface DeliveryOrderQuery {
  /** 发货单号 */
  deliveryNo?: string
  /** 订单ID */
  orderId?: number
  /** 店铺ID */
  shopId?: number
  /** 状态 */
  status?: string
}

/** 嵌套结构(引用自 openapi schema DeliveryOrderItemResponse) */
export interface DeliveryOrderItemResponse {
  /** id */
  id: number
  /** deliveryId */
  deliveryId: number
  /** orderItemId */
  orderItemId: number
  /** skuId */
  skuId: number
  /** shipQty */
  shipQty: number
  /** createdAt */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 新增发货单入参(gen:page 从 openapi 快照生成;readonly 域人工扩展,#11 建发货单表单槽位)
 *  status 服务端置 PENDING、shop_id 按订单回填、sku_id 服务端回填,均不入参 */
export interface DeliveryOrderSaveRequest {
  /** 发货单号(≤64) */
  deliveryNo: string
  /** 订单ID(仅 WAIT_SHIP 且卖家自履约订单可建,后端校验) */
  orderId: number
  /** 出库仓ID */
  warehouseId: number
  /** 类型(系统发货单固定 SELF_FULFILL;FBA/海外仓平台履约不产生系统发货单) */
  type?: string
  /** 承诺发货时限 */
  shipByTime?: string
  /** 物流公司(≤64) */
  logisticsCompany?: string
  /** 运单号(≤64,可后补) */
  trackingNo?: string
  /** waybillUrl(≤512) */
  waybillUrl?: string
  /** 发货明细(仅 sku_id 已绑定订单行) */
  items: DeliveryOrderItemSaveRequest[]
}

/** 发货明细行(orderItemId 归属校验在后端,sku_id 服务端按订单行回填) */
export interface DeliveryOrderItemSaveRequest {
  /** 订单明细行ID */
  orderItemId: number
  /** 本次发货数量 */
  shipQty: number
}
