/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 店铺管理接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 店铺管理实体(分页行 / 详情) */
export interface ShopResponse {
  /** id */
  id: number
  /** merchantId */
  merchantId: number
  /** 平台 */
  platform: string
  /** 店铺名称 */
  shopName: string
  /** 卖家ID */
  sellerId: string
  /** 平台应用Key */
  appKey: string
  /** 授权凭证 */
  accessToken: string
  /** 令牌过期时间 */
  tokenExpireAt: string
  /** 状态 */
  status: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 店铺管理新增/修改入参 */
export interface ShopSaveRequest {
  /** platform */
  platform: string
  /** shopName */
  shopName?: string
  /** sellerId */
  sellerId?: string
  /** appKey */
  appKey?: string
  /** appSecret */
  appSecret?: string
  /** accessToken */
  accessToken?: string
  /** refreshToken */
  refreshToken?: string
  /** tokenExpireAt */
  tokenExpireAt?: string
  /** status */
  status?: number
}

/** 店铺管理分页查询入参(不含分页参数) */
export interface ShopQuery {
  /** 平台 */
  platform?: string
  /** 状态 */
  status?: number
}
