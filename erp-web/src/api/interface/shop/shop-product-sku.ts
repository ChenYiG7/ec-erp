/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** SKU匹配接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** SKU匹配实体(分页行 / 详情) */
export interface ShopProductSkuResponse {
  /** id */
  id: number
  /** 平台商品ID */
  shopProductId: number
  /** 卖家SKU */
  sellerSku: string
  /** 内部SKU */
  skuId: number
  /** 可售数量 */
  quantity: number
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 平台售价 */
  price: string
  /** 币种 */
  currency: string
  /** 匹配状态 */
  matchStatus: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** SKU匹配动作请求体 */
export interface ShopProductSkuBindRequest {
  /** skuId */
  skuId: number
}

/** SKU匹配分页查询入参(不含分页参数) */
export interface ShopProductSkuQuery {
  /** 平台商品ID */
  shopProductId?: number
  /** 内部SKU */
  skuId?: number
  /** 匹配状态 */
  matchStatus?: number
}
