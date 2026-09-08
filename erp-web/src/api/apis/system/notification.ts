import http from '@/utils/request'
import type { PageQuery, PageResult, SysNotificationResponse } from '@/api/interface'

export interface NotificationQuery extends PageQuery {
  /** 0未读 1已读,不传=全部 */
  readStatus?: number
  /** 通知类型,如 PULL_FAIL */
  notifyType?: string
}

/**
 * 站内通知(/api/system/notifications,系统告警扇出只读 + 本人已读状态)
 * 无 SSE/WebSocket,红点走轮询(stores/modules/notification.ts,60s + 页面隐藏暂停)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const NotificationApi = {
  page: (params: NotificationQuery) =>
    http.get<PageResult<SysNotificationResponse>>('/api/system/notifications', params).then(page => ({ list: page.records, total: page.total })),
  /** 未读数(铃铛红点,99 封顶在组件侧处理) */
  unreadCount: () => http.get<number>('/api/system/notifications/unread-count'),
  /** 单条标记已读 */
  read: (id: number) => http.put<void>(`/api/system/notifications/${id}/read`),
  /** 全部标记已读 */
  readAll: () => http.put<void>('/api/system/notifications/read-all')
}
