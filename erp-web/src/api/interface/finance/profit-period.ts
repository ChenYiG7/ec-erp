/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 周期利润接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 周期利润实体(分页行 / 详情) */
export interface ProfitPeriodReportResponse {
  /** id */
  id: number
  /** 店铺ID */
  shopId: number
  /** settlementId */
  settlementId: number
  /** 周期起 */
  periodStart: string
  /** 周期止 */
  periodEnd: string
  /** 币种 */
  currency: string
  /** 折算汇率 */
  rateUsed: number
  /** 缺汇率 */
  rateMissing: number
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 订单收入 */
  orderIncome: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 结算回款 */
  settleIncome: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 结算佣金 */
  settleCommission: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** FBA费用 */
  fbaFee: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 其他费用 */
  otherFee: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 订单佣金 */
  orderCommission: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 订单利润 */
  orderProfit: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 收入校差 */
  diffIncome: string
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 佣金校差 */
  diffCommission: string
  /** 差异 */
  diffFlag: number
  /** diffRemark */
  diffRemark: string
  /** 状态 */
  status: string
  /** createdAt */
  createdAt: string
  /** updatedAt */
  updatedAt: string
}

/** 周期利润分页查询入参(不含分页参数) */
export interface ProfitPeriodReportQuery {
  /** 店铺ID */
  shopId?: number
  /** 状态 */
  status?: string
}
