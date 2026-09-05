/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 店铺商品接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 店铺商品实体(分页行 / 详情) */
export interface ShopProductResponse {
  /** id */
  id: number
  /** 店铺ID */
  shopId: number
  /** 平台商品ID */
  platformProductId: string
  /** platformSkuId */
  platformSkuId: string
  /** 内部SPU */
  productId: number
  /** listing状态 */
  listingStatus: string
  /** 最近同步 */
  lastSyncAt: string
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 店铺商品分页查询入参(不含分页参数) */
export interface ShopProductQuery {
  /** 店铺ID */
  shopId?: number
  /** 内部SPU */
  productId?: number
  /** 平台商品ID */
  platformProductId?: string
}
