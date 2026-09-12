import http from '@/utils/request'
import type { SysDeptSaveRequest, DeptNode } from '@/api/interface/system/dept'

/**
 * 部门管理接口(#27③,/api/system/depts;手写——树形域无分页端点,gen:page 不适用,同分类管理先例)
 * 读写限 admin 角色(后端 @PreAuthorize hasRole('admin') 双闸,permKey system:dept:* 前端收口)
 */
export const sysDeptApi = {
  /** 全量部门树(parent_id=0 为根,按 sort 升序;含禁用节点) */
  tree: () => http.get<DeptNode[]>(`/api/system/depts/tree`),
  /** 新增(后端返回主键) */
  create: (data: SysDeptSaveRequest) => http.post<number>(`/api/system/depts`, data),
  /** 修改(成环校验在后端) */
  update: (id: number, data: SysDeptSaveRequest) => http.put<boolean>(`/api/system/depts/${id}`, data),
  /** 删除(有子部门/用户引用由后端拦截) */
  remove: (id: number) => http.delete<boolean>(`/api/system/depts/${id}`),
}
