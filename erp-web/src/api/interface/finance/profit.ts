/**
 * 实时销售利润接口类型(#19③,对齐后端契约 ProfitQueryApi,禁手抄漂移)
 * 口径:利润 = 售价(CNY) − 出库成本(CNY) − 平台佣金(CNY);缺成本=未出库,缺佣金=待结算,缺汇率=本位币空
 */

/** 订单行利润行(GET /api/finance/profit) */
export interface OrderProfitRow {
  /** 订单行ID(shop_order_item.id) */
  orderItemId: number
  orderId: number
  platformOrderId: string
  platform: string
  /** 下单时间(汇率回溯锚点) */
  orderTime: string
  shopId: number
  /** 平台订单行号(佣金归集键,可空) */
  platformOrderItemId: string | null
  platformSku: string | null
  productName: string | null
  /** 内部SKU(可空=未绑定) */
  skuId: number | null
  quantity: number
  /** 小计金额(原币) */
  itemAmount: string
  currency: string
  /** 下单日回溯汇率(缺报价 null) */
  rate: string | null
  /** 售价(CNY),缺汇率 null */
  salesCny: string | null
  /** 出库成本(CNY),未出库 null */
  costCny: string | null
  /** 平台佣金(CNY,报告原值带符号为负),待结算 null */
  commissionCny: string | null
  /** 利润(CNY),缺成本/缺汇率 null;仅缺佣金=售价−成本(毛利) */
  profitCny: string | null
  /** true=未出库 */
  costMissing: boolean
  /** true=待结算 */
  commissionMissing: boolean
}

/** 利润汇总(GET /api/finance/profit/summary;缺口单独计数不静默归零) */
export interface OrderProfitSummary {
  orderItemCount: number
  salesCny: string
  costCny: string
  commissionCny: string
  profitCny: string
  /** 缺汇率行数 */
  missingRateCount: number
  /** 未出库行数 */
  costMissingCount: number
  /** 待结算行数 */
  commissionMissingCount: number
}

/** 利润查询入参(不含分页;后端 OrderProfitPageQuery) */
export interface OrderProfitQuery {
  shopId?: number
  platform?: string
  skuId?: number
  /** 下单时间起(含,yyyy-MM-dd HH:mm:ss) */
  dateFrom?: string
  /** 下单时间止(不含) */
  dateTo?: string
}

/** 利润日趋势行(GET /api/finance/profit/trend,#21 利润看板;按下单日聚合,口径与汇总同源) */
export interface ProfitDailyTrendRow {
  /** 统计日(yyyy-MM-dd) */
  statDate: string
  orderItemCount: number
  salesCny: string
  costCny: string
  commissionCny: string
  profitCny: string
}

/** SKU 利润排行行(GET /api/finance/profit/sku-rank;仅已绑定行,利润降序) */
export interface ProfitSkuRankRow {
  skuId: number
  productName: string
  orderItemCount: number
  quantity: number
  salesCny: string
  costCny: string
  commissionCny: string
  profitCny: string
}
