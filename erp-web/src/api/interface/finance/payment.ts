/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/** 资金流水接口类型(gen:page 从 openapi 快照生成;对齐后端契约,禁手抄字段) */

/** 资金流水实体(分页行 / 详情) */
export interface PaymentRecordResponse {
  /** id */
  id: number
  /** 流水号 */
  paymentNo: string
  /** 方向 */
  direction: string
  /** 类型 */
  bizType: string
  /** 往来方类型 */
  partyType: string
  /** partyId */
  partyId: number
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 原币金额 */
  amount: string
  /** 币种 */
  currency: string
  /** exchangeRate */
  exchangeRate: number
  // 金额(后端 DECIMAL(12,4)),禁 parseFloat/Number 参与计算
  /** 折算CNY */
  amountCny: string
  /** 收付款时间 */
  paidAt: string
  /** 结算方式 */
  method: string
  /** refType */
  refType: string
  /** refId */
  refId: number
  /** 状态 */
  status: string
  /** 备注 */
  remark: string
  /** createdBy */
  createdBy: number
  /** 创建时间 */
  createdAt: string
  /** updatedAt */
  updatedAt: string
  /** 往来方 */
  partyName: string
}

/** 资金流水分页查询入参(不含分页参数) */
export interface PaymentRecordQuery {
  /** 方向 */
  direction?: string
  /** 类型 */
  bizType?: string
  /** 往来方类型 */
  partyType?: string
  /** partyId */
  partyId?: number
  /** paidFrom */
  paidFrom?: string
  /** paidTo */
  paidTo?: string
  /** includeVoided */
  includeVoided?: boolean
}

/** 资金流水分摊行(采购付款→采购单) */
export interface PaymentAllocResponse {
  /** id */
  id: number
  /** 采购单ID */
  poId: number
  /** 采购单号 */
  poNo: string
  /** 分摊金额(CNY,后端 DECIMAL(12,4)) */
  amount: string
}

/** 资金流水详情(主体 + 分摊行;仅采购付款有行) */
export interface PaymentDetailResponse {
  /** 流水主体 */
  payment: PaymentRecordResponse
  /** 分摊明细(结算/手工流水为空数组) */
  allocs: PaymentAllocResponse[]
}

/** 采购付款登记分摊行 */
export interface PurchasePaymentAlloc {
  /** 采购单ID(须已审核) */
  poId: number
  /** 分摊金额(CNY,恒正;已付+本次 ≤ 采购总额) */
  amount: string
}

/** 采购付款登记入参(POST /purchase;强制 CNY,一付多单) */
export interface PurchasePaymentRequest {
  /** 付款金额(CNY,≥ Σ 分摊,允许部分挂账) */
  amount: string
  /** 收付款时间(空=后端当前时间,yyyy-MM-dd HH:mm:ss) */
  paidAt?: string
  /** 结算方式 */
  method?: string
  /** 备注 */
  remark?: string
  /** 采购单分摊行(至少一行) */
  allocs: PurchasePaymentAlloc[]
}

/** 手工资金登记入参(POST /manual;非 CNY 按 paidAt 回溯汇率) */
export interface ManualPaymentRequest {
  /** EXPENSE 付款 / INCOME 回款 */
  direction: string
  /** SUPPLIER / PLATFORM / OTHER */
  partyType: string
  /** 往来方ID(OTHER 可空) */
  partyId?: number
  /** 原币金额 */
  amount: string
  /** 币种(空按 CNY) */
  currency?: string
  /** 收付款时间(空=后端当前时间) */
  paidAt?: string
  /** 结算方式 */
  method?: string
  /** 备注 */
  remark?: string
}

/** 采购单已付聚合行(采购列表/详情资金视图) */
export interface PurchasePaidRow {
  /** 采购单ID */
  poId: number
  /** 采购总金额(CNY,有分摊时随聚合返回) */
  totalAmount?: string
  /** 已付金额(Σ NORMAL 分摊,无分摊为 0) */
  paidAmount: string
  /** 待付金额(总额 − 已付,SQL DECIMAL 侧计算;无分摊行缺省) */
  unpaidAmount?: string
}

/** 供应商应付视图行 */
export interface SupplierPayableRow {
  /** 供应商ID */
  supplierId: number
  /** 供应商名称 */
  supplierName: string
  /** 账期天数(V1 仅展示) */
  settleDays?: number
  /** 应付(Σ 非草稿采购单总额,CNY) */
  payableAmount: string
  /** 已付(Σ NORMAL 分摊,CNY) */
  paidAmount: string
  /** 待付(应付 − 已付,CNY) */
  unpaidAmount: string
}

/** 平台回款视图行(按店铺聚合) */
export interface PlatformReceiptRow {
  /** 店铺ID */
  shopId: number
  /** 店铺名称 */
  shopName?: string
  /** 回款笔数 */
  receiptCount: number
  /** 回款原币金额(跨币种混合参考) */
  amount: string
  /** 回款折合 CNY(缺汇率行不计入) */
  amountCny: string
  /** 缺汇率行数(前端显"折算缺失") */
  missingRateCount: number
}

/** 期间资金汇总(CNY 口径) */
export interface PaymentSummaryResponse {
  /** 期间起 */
  paidFrom?: string
  /** 期间止 */
  paidTo?: string
  /** 回款合计 CNY(后端 BigDecimal 序列化为 number;展示禁参与精度计算,docs/09 §6) */
  incomeCny: number
  /** 付款合计 CNY */
  expenseCny: number
  /** 收付净额(回款 − 付款)CNY */
  netCny: number
  /** 流水笔数(含缺汇率行) */
  totalCount: number
  /** 缺汇率行数(金额未进上面合计) */
  missingRateCount: number
}
