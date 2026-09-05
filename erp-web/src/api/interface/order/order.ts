/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 订单管理接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 订单管理实体(分页行 / 详情) */
export interface ShopOrderResponse {
  /** id */
  id: number
  /** 店铺ID */
  shopId: number
  /** 平台 */
  platform: string
  /** 平台单号 */
  platformOrderId: string
  /** 订单状态 */
  orderStatus: string
  /** 履约渠道 */
  fulfillmentChannel: string
  /** 下单时间 */
  orderTime: string
  /** 付款时间 */
  paidTime: string
  /** buyerNote */
  buyerNote: string
  /** receiverName */
  receiverName: string
  /** receiverPhone */
  receiverPhone: string
  /** receiverCountry */
  receiverCountry: string
  /** receiverState */
  receiverState: string
  /** receiverCity */
  receiverCity: string
  /** receiverAddress */
  receiverAddress: string
  /** receiverZip */
  receiverZip: string
  /** 币种 */
  currency: string
  /** exchangeRate */
  exchangeRate: number
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 订单金额 */
  orderAmount: string
  /** shippingFee */
  shippingFee: number
  /** discountAmount */
  discountAmount: number
  /** createdAt */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** items */
  items: ShopOrderItemResponse[]
}

/** 订单管理分页查询入参(不含分页参数) */
export interface ShopOrderQuery {
  /** 店铺ID */
  shopId?: number
  /** 平台 */
  platform?: string
  /** 订单状态 */
  orderStatus?: string
}

/** 嵌套结构(引用自 openapi schema ShopOrderItemResponse) */
export interface ShopOrderItemResponse {
  /** id */
  id: number
  /** orderId */
  orderId: number
  /** platformOrderItemId */
  platformOrderItemId: string
  /** shopProductSkuId */
  shopProductSkuId: number
  /** skuId */
  skuId: number
  /** platformSku */
  platformSku: string
  /** productName */
  productName: string
  /** quantity */
  quantity: number
  /** unitPrice */
  unitPrice: number
  /** itemAmount */
  itemAmount: number
  /** currency */
  currency: string
}
