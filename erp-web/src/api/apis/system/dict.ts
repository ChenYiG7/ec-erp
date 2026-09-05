import http from '@/utils/request'
import type { DictItem, PageQuery, PageResult } from '@/api/interface'
import type { SysDictResponse, SysDictSaveRequest, SysDictQuery } from '@/api/interface/system/dict'

/**
 * 字典接口(/api/system/dicts)
 * DictApi.getDictByType 为 P2 手写的启用项查询(dict store 惰性加载缓存用);
 * sysDictApi 为管理端 CRUD(gen:page 产出,人工并入本文件;重生成 --force 后需重新并入 DictApi);
 * 分页差异只在 sysDictApi.page 内单点收口
 */
export const DictApi = {
  getDictByType: (dictType: string) => http.get<DictItem[]>(`/api/system/dicts/type/${dictType}`)
}

export const sysDictApi = {
  /** 分页查询(入参 pageNo/pageSize,返回 {list,total}) */
  page: (params: SysDictQuery & PageQuery) =>
    http.get<PageResult<SysDictResponse>>('/api/system/dicts', params).then(page => ({ list: page.records, total: page.total })),
  /** 新增(后端返回主键) */
  create: (data: SysDictSaveRequest) => http.post<number>(`/api/system/dicts`, data),
  /** 修改 */
  update: (id: number, data: SysDictSaveRequest) => http.put<boolean>(`/api/system/dicts/${id}`, data),
  /** 删除 */
  remove: (id: number) => http.delete<boolean>(`/api/system/dicts/${id}`),
}
