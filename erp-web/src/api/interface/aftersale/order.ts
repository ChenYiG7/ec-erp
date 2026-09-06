/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 售后单接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 售后单实体(分页行 / 详情) */
export interface AftersaleOrderResponse {
  /** id */
  id: number
  /** 售后单号 */
  aftersaleNo: string
  /** 店铺ID */
  shopId: number
  /** platformRefundId */
  platformRefundId: string
  /** 订单ID */
  orderId: number
  /** warehouseId */
  warehouseId: number
  /** 类型 */
  type: string
  /** 状态 */
  status: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 退款金额 */
  refundAmount: string
  /** 币种 */
  currency: string
  /** 售后原因 */
  reason: string
  /** 处理结果 */
  result: string
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** returnItems */
  returnItems: AftersaleReturnItemResponse[]
}

/** 售后单动作请求体 */
export interface AftersaleOrderRejectRequest {
  /** result */
  result?: string
}

/** 售后单动作请求体 */
export interface AftersaleOrderRefundRequest {
  /** result */
  result?: string
}

/** 售后单动作请求体 */
export interface AftersaleOrderReceiveReturnRequest {
  /** warehouseId */
  warehouseId: number
  /** items */
  items: AftersaleReturnItemRequest[]
  /** result */
  result?: string
}

/** 售后单动作请求体 */
export interface AftersaleOrderCompleteRequest {
  /** result */
  result?: string
}

/** 售后单动作请求体 */
export interface AftersaleOrderAgreeRequest {
  /** result */
  result?: string
}

/** 售后单分页查询入参(不含分页参数) */
export interface AftersaleOrderQuery {
  /** 店铺ID */
  shopId?: number
  /** 状态 */
  status?: string
  /** 类型 */
  type?: string
  /** 订单ID */
  orderId?: number
}

/** 嵌套结构(引用自 openapi schema AftersaleReturnItemResponse) */
export interface AftersaleReturnItemResponse {
  /** id */
  id: number
  /** orderItemId */
  orderItemId: number
  /** skuId */
  skuId: number
  /** returnQty */
  returnQty: number
}

/** 嵌套结构(引用自 openapi schema AftersaleReturnItemRequest) */
export interface AftersaleReturnItemRequest {
  /** orderItemId */
  orderItemId: number
  /** returnQty */
  returnQty: number
}
