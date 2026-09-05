/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 商品管理接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 商品管理实体(分页行 / 详情) */
export interface ProductResponse {
  /** id */
  id: number
  /** SPU编码 */
  spuCode: string
  /** 商品名称 */
  name: string
  /** 分类ID */
  categoryId: number
  /** 品牌ID */
  brandId: number
  /** attrsJson */
  attrsJson: string
  /** 状态 */
  status: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 商品管理分页查询入参(不含分页参数) */
export interface ProductQuery {
  /** 关键字 */
  keyword?: string
  /** 分类ID */
  categoryId?: number
}
