/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 商品SKU接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段)
 *  #16 SKU 搜索选择器槽位:契约无全局 SKU 搜索端点(GET /api/goods/skus 仅 SPU 内列表),
 *  类型随快照手工登记,选择器组件收口 src/components/SkuSelector */

/** 商品SKU(商品库 product_sku;金额 DECIMAL(12,4) 按 string 红线,禁 parseFloat/Number 运算) */
export interface ProductSkuResponse {
  /** id */
  id: number
  /** SPU ID */
  productId: number
  /** SKU 编码(全局唯一) */
  skuCode: string
  /** 条码 */
  barcode: string
  /** 销售属性 JSON */
  attrsJson: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 成本价 */
  costPrice: string
  /** 重量(克) */
  weightG: number
  /** 海关编码 */
  hsCode: string
  /** 申报价值 */
  declaredValue: string
  /** 是否带电(1是0否) */
  battery: number
  /** 状态(1启用0禁用) */
  status: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** SKU 选项/翻译(#7 专条:GET /api/goods/skus/batch?ids=,跨页 skuId 列翻译数据源;productName 为 SPU 名称,SPU 已删时缺省) */
export interface SkuOptionResponse {
  /** id */
  id: number
  /** SKU 编码(全局唯一) */
  skuCode: string
  /** 所属 SPU 名称 */
  productName?: string
}
