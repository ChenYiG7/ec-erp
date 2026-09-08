import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type {
  ExchangeRateQuery,
  ExchangeRateResponse,
  ExchangeRateSaveRequest,
} from '@/api/interface/finance/exchange-rate'

/** 汇率快照(/api/finance/exchange-rates,#19③;写侧 admin 双闸) */
export const exchangeRateApi = {
  /** 分页查询(按报价时间倒序) */
  page: (params: ExchangeRateQuery & PageQuery) =>
    http
      .get<PageResult<ExchangeRateResponse>>('/api/finance/exchange-rates', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 手工录入快照(source 固定 MANUAL,admin) */
  save: (data: ExchangeRateSaveRequest) => http.post<number>('/api/finance/exchange-rates', data),
}
