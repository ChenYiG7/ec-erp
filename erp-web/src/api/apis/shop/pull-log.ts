import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { PullLogResponse, PullLogQuery } from '@/api/interface/shop/pull-log'

/**
 * 拉单日志(/api/pull-logs,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const pullLogApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: PullLogQuery & PageQuery) =>
    http.get<PageResult<PullLogResponse>>('/api/pull-logs', params).then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<PullLogResponse>(`/api/pull-logs/${id}`),
}