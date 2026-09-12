import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { SysOperLogResponse, SysOperLogQuery } from '@/api/interface/system/oper-log'

/**
 * 操作日志(/api/system/oper-logs,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const sysOperLogApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: SysOperLogQuery & PageQuery) =>
    http
      .get<PageResult<SysOperLogResponse>>('/api/system/oper-logs', params)
      .then(page => ({ list: page.records, total: page.total })),
}
