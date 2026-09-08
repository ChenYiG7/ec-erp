/**
 * 汇率快照接口类型(#19③,对齐后端契约 ExchangeRateResponse/Service)
 * 汇率是快照不是现值:利润折算按业务日回溯取最近报价;本位币 V1 固定 CNY
 */

/** 汇率快照行(GET /api/finance/exchange-rates) */
export interface ExchangeRateResponse {
  id: number
  /** 币种(ISO 4217) */
  currency: string
  /** 汇率(1 currency = rate CNY) */
  rate: string
  /** 报价时间 */
  quotedAt: string
  /** MANUAL/API */
  source: string
  createdAt: string
}

/** 汇率手工录入入参(POST /api/finance/exchange-rates,admin) */
export interface ExchangeRateSaveRequest {
  currency: string
  rate: string
  /** 报价时间(yyyy-MM-dd HH:mm:ss) */
  quotedAt: string
}

/** 汇率查询入参(不含分页) */
export interface ExchangeRateQuery {
  currency?: string
}
