import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { ShopResponse, ShopSaveRequest, ShopQuery } from '@/api/interface/shop/shop'

/**
 * 店铺管理(/api/shops,由 gen:page 生成)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const shopApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: ShopQuery & PageQuery) =>
    http.get<PageResult<ShopResponse>>('/api/shops', params).then(page => ({ list: page.records, total: page.total })),
  /** 详情 */
  detail: (id: number) => http.get<ShopResponse>(`/api/shops/${id}`),
  /** 新增(后端返回主键) */
  create: (data: ShopSaveRequest) => http.post<number>(`/api/shops`, data),
  /** 修改 */
  update: (id: number, data: ShopSaveRequest) => http.put<boolean>(`/api/shops/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/shops/${id}`),
  /** 生成平台授权跳转地址(GET /api/shops/{id}/auth-url) */
  authUrl: (id: number) => http.get<string>(`/api/shops/${id}/auth-url`),
}

// ⚠️ 以下端点未归类(gen:page 不生成,人工到本文件补齐并登记 TODO 编号):
//   GET /api/shops/oauth/callback — 平台授权回调(OAuth)
