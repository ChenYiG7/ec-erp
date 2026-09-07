import http from '@/utils/request'
import type { SysConfig } from '@/api/interface'

/**
 * 系统参数(/api/system/configs,#18 系统设置):
 * 读侧登录即可(按组取合并视图:词表全量键 × DB 覆盖值);写侧 admin(词表白名单+类型校验后端收口)
 */
export const SystemConfigApi = {
  /** 按组取参数(DB 无行的键也给占位行,configValue 为空 = 前端渲染代码默认值) */
  listByGroup: (group: string) => http.get<SysConfig[]>(`/api/system/configs/group/${group}`),
  /** 保存一组参数(upsert;空值 = 删覆盖行回落代码默认值;返回生效键数) */
  saveGroup: (group: string, values: Record<string, string>) =>
    http.put<number>(`/api/system/configs/group/${group}`, values),
}
