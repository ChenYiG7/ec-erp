/** 部门节点(#27③ 手写,按 tools/openapi.json 快照 DeptNode 核对;GET /api/system/depts/tree) */
export interface DeptNode {
  id: number
  parentId: number
  deptName: string
  sort: number
  /** 状态:1 启用 / 0 停用 */
  status: number
  children?: DeptNode[]
}

/** 部门新增/修改入参(#27③ 手写;deptName 必填,parentId 不传按根处理,成环校验在后端) */
export interface SysDeptSaveRequest {
  parentId?: number
  deptName: string
  sort?: number
  /** 状态:1 启用 / 0 停用 */
  status?: number
}
