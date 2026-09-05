import { defineStore } from 'pinia'
import { ref } from 'vue'
import { NotificationApi } from '@/api/apis/system/notification'

/**
 * 站内通知红点 store
 * 后端无 SSE/WebSocket(docs/09 §3):60s 轮询 unread-count;
 * 页面隐藏暂停、回前台的立刷一次;登录后启动,登出清理
 */
export const useNotificationStore = defineStore('erp-notification', () => {
  /** 未读数(>0 显示,99 封顶在组件侧) */
  const unreadCount = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null

  const refresh = async () => {
    try {
      const count = await NotificationApi.unreadCount()
      unreadCount.value = Number(count) || 0
    } catch {
      // 静默:红点轮询失败不打扰用户(登录态问题由拦截器统一处理)
    }
  }

  const start = () => {
    stop()
    refresh()
    timer = setInterval(refresh, 60_000)
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }

  // 页面隐藏暂停轮询,回前台立即刷一次并恢复
  const onVisibilityChange = () => {
    if (document.hidden) {
      if (timer) {
        clearInterval(timer)
        timer = null
      }
    } else {
      refresh()
      timer = setInterval(refresh, 60_000)
    }
  }

  return { unreadCount, refresh, start, stop }
})
