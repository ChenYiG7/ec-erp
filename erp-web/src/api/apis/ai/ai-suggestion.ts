import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { AiSuggestionResponse, AiSuggestionQuery } from '@/api/interface/ai/ai-suggestion'

/**
 * AI建议(/api/ai/suggestions,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const aiSuggestionApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: AiSuggestionQuery & PageQuery) =>
    http
      .get<PageResult<AiSuggestionResponse>>('/api/ai/suggestions', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<AiSuggestionResponse>(`/api/ai/suggestions/${id}`),
  /** 忽略建议(POST /api/ai/suggestions/{id}/ignore) */
  ignore: (id: number) => http.post<unknown>(`/api/ai/suggestions/${id}/ignore`),
  /** 采纳建议(POST /api/ai/suggestions/{id}/adopt) */
  adopt: (id: number) => http.post<unknown>(`/api/ai/suggestions/${id}/adopt`),
}
