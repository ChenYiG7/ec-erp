import http from '@/utils/request'
import { postSse } from '@/utils/sse'
import type { NotificationFrame, PageQuery, PageResult, SysNotificationResponse } from '@/api/interface'

export interface NotificationQuery extends PageQuery {
  /** 0未读 1已读,不传=全部 */
  readStatus?: number
  /** 通知类型,如 PULL_FAIL */
  notifyType?: string
}

/**
 * 站内通知(/api/system/notifications,系统告警扇出只读 + 本人已读状态)
 * 双通道:60s 轮询兜底 + SSE 实时推送(stores/modules/notification.ts;SSE 健康挂起轮询,断线退避重连)
 * 分页字段差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const NotificationApi = {
  page: (params: NotificationQuery) =>
    http
      .get<PageResult<SysNotificationResponse>>('/api/system/notifications', params)
      .then(page => ({ list: page.records, total: page.total })),
  /** 未读数(铃铛红点,99 封顶在组件侧处理) */
  unreadCount: () => http.get<number>('/api/system/notifications/unread-count'),
  /** 单条标记已读 */
  read: (id: number) => http.put<void>(`/api/system/notifications/${id}/read`),
  /** 全部标记已读 */
  readAll: () => http.put<void>('/api/system/notifications/read-all'),
  /**
   * SSE 订阅实时推送:通知帧 JSON + 30s 心跳帧{type:HEARTBEAT};帧解析/鉴权/错误收口 utils/sse
   * (postSse,同 chat/agent;EventSource 带不了 Bearer 禁用);signal 供页面隐藏/登出断流
   */
  subscribe: (onEvent: (frame: NotificationFrame) => void, signal?: AbortSignal) =>
    postSse(
      '/api/system/notifications/subscribe',
      {},
      payload => {
        try {
          onEvent(JSON.parse(payload) as NotificationFrame)
        } catch {
          // 非法帧跳过:红点通道静默,不打扰用户
        }
      },
      signal
    ),
}
