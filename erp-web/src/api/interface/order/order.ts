/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 * 人工改动(#29 订单域补课):补订单来源/审核状态字段与内销录单、审核动作入参(契约以 api:sync 快照为准)
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
  /** 订单来源:PLATFORM 平台拉单 / MANUAL 内销手工录单(#29) */
  orderSource: string
  /** 审核状态:0无需审核/1待审核/2已通过/3已驳回(#29) */
  reviewStatus: number
  /** 审核/风控备注(#29) */
  reviewRemark?: string
  /** 审核人(sys_user.id,#29) */
  reviewedBy?: number
  /** 审核时间(#29) */
  reviewedAt?: string
  /** 命中风控规则摘要,空=未命中(#29) */
  riskFlag?: string
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
  /** 订单来源:PLATFORM/MANUAL(#29) */
  orderSource?: string
  /** 审核状态:0/1/2/3(#29) */
  reviewStatus?: number
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

/** 订单审核动作入参(#29;审核人/时间后端回填) */
export interface ShopOrderReviewCommand {
  /** true=通过 / false=驳回 */
  approve: boolean
  /** 审核/风控备注(可空) */
  remark?: string
}

/** 内销订单明细入参(#29;单价 DECIMAL 序列化为 string,禁 Number 参与计算,docs/09 §6) */
export interface ManualOrderItemSaveRequest {
  /** 内部SKU ID,必绑 */
  skuId: number
  /** 商品名称快照(选填) */
  productName?: string
  /** 数量(≥1) */
  quantity: number
  /** 单价(原币,string) */
  unitPrice: string
}

/** 内销订单录单入参(#29;平台/单号/状态/金额/审核态均由后端派生,不入参) */
export interface ManualOrderSaveRequest {
  /** 店铺ID */
  shopId: number
  /** 买家留言(命中风控关键词则进待审核) */
  buyerNote?: string
  /** 收货人姓名 */
  receiverName: string
  /** 收货人电话 */
  receiverPhone: string
  /** 收货国家(ISO 3166) */
  receiverCountry: string
  /** 收货省/州 */
  receiverState?: string
  /** 收货城市 */
  receiverCity: string
  /** 收货详细地址 */
  receiverAddress: string
  /** 收货邮编 */
  receiverZip: string
  /** 币种(空=CNY) */
  currency?: string
  /** 汇率快照(空=1) */
  exchangeRate?: number
  /** 订单明细 */
  items: ManualOrderItemSaveRequest[]
}
