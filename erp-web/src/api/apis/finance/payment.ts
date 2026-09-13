import type { PageQuery, PageResult } from '@/api/interface'
import type {
  ManualPaymentRequest,
  PaymentDetailResponse,
  PaymentRecordQuery,
  PaymentRecordResponse,
  PaymentSummaryResponse,
  PlatformReceiptRow,
  PurchasePaidRow,
  PurchasePaymentRequest,
  SupplierPayableRow,
} from '@/api/interface/finance/payment'
import http from '@/utils/request'

/**
 * 资金流水(/api/finance/payments)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const paymentRecordApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PaymentRecordQuery & PageQuery) =>
    http
      .get<PageResult<PaymentRecordResponse>>('/api/finance/payments', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情(带采购分摊行) */
  detail: (id: number) => http.get<PaymentDetailResponse>(`/api/finance/payments/${id}`),
  /** 作废流水(POST /{id}/void,仅 NORMAL;admin) */
  void: (id: number) => http.post<unknown>(`/api/finance/payments/${id}/void`),
  /** 采购付款登记(POST /purchase,一笔付款分摊多张采购单;admin) */
  registerPurchase: (data: PurchasePaymentRequest) => http.post<number>('/api/finance/payments/purchase', data),
  /** 手工资金登记(POST /manual,补录非结算回款/其他收付款;admin) */
  registerManual: (data: ManualPaymentRequest) => http.post<number>('/api/finance/payments/manual', data),
  /** 采购单已付批量(采购列表/详情资金视图) */
  purchasePaid: (poIds: number[]) => http.post<PurchasePaidRow[]>('/api/finance/payments/purchase-paid', poIds),
  /** 供应商应付视图分页 */
  supplierParties: (params: PageQuery) =>
    http
      .get<PageResult<SupplierPayableRow>>('/api/finance/payments/parties/suppliers', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 平台回款视图(按店铺聚合期间回款) */
  platformParties: (params: { paidFrom?: string; paidTo?: string }) =>
    http.get<PlatformReceiptRow[]>('/api/finance/payments/parties/platforms', params),
  /** 期间资金汇总(CNY 收付净额 + 缺汇率行数) */
  summary: (params: { paidFrom?: string; paidTo?: string }) =>
    http.get<PaymentSummaryResponse>('/api/finance/payments/summary', params),
}
