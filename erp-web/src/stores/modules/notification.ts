import { defineStore } from 'pinia'
import { ref } from 'vue'
import { NotificationApi } from '@/api/apis/system/notification'
import type { NotificationFrame } from '@/api/interface'

/**
 * 站内通知红点 store
 * 双通道(SSE 计划书):SSE 实时推送为主(订阅 /subscribe,AFTER_COMMIT 帧),
 * 60s 轮询为断线降级——SSE 帧到达(含心跳)= 连接健康挂起轮询;断开立即恢复轮询兜底
 * + 指数退避重连(1s 起 ×2 封顶 30s,抖动 ±20%);回前台立即 refresh 并确保 SSE 连接;
 * 登录后启动,登出清理(AbortController 断流)
 */
export const useNotificationStore = defineStore('erp-notification', () => {
  const POLL_INTERVAL = 60_000
  const RECONNECT_BASE_MS = 1_000
  const RECONNECT_MAX_MS = 30_000

  /** 未读数(>0 显示,99 封顶在组件侧) */
  const unreadCount = ref(0)
  let timer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  /** SSE 流控制器(断流唯一手段:postSse 走 fetch,无 signal 则无法主动断) */
  let controller: AbortController | null = null
  /** SSE 健康(任意帧到达)——健康期轮询挂起 */
  let sseActive = false
  /** 建连中(请求已发出未收到首帧)——防回前台重复建连 */
  let connecting = false
  /** 重连退避次数(收到任意帧即清零) */
  let reconnectAttempt = 0
  /** stop() 后不再自愈重连(登出/布局卸载) */
  let stopped = false

  const refresh = async () => {
    try {
      const count = await NotificationApi.unreadCount()
      unreadCount.value = Number(count) || 0
    } catch {
      // 静默:红点轮询失败不打扰用户(登录态问题由拦截器统一处理)
    }
  }

  const startPolling = () => {
    if (!timer) {
      timer = setInterval(refresh, POLL_INTERVAL)
    }
  }

  const stopPolling = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  const closeSse = () => {
    controller?.abort()
    controller = null
    sseActive = false
    connecting = false
  }

  /** 断连统一入口:恢复轮询兜底 + 指数退避重连;登出/页面隐藏不重连 */
  const scheduleReconnect = () => {
    connecting = false
    sseActive = false
    if (stopped || document.hidden) {
      return
    }
    startPolling()
    const backoff = Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS * 2 ** reconnectAttempt)
    const delay = Math.round(backoff * (0.8 + Math.random() * 0.4))
    reconnectAttempt += 1
    reconnectTimer = setTimeout(connectSse, delay)
  }

  const connectSse = () => {
    reconnectTimer = null
    if (stopped || document.hidden) {
      return
    }
    controller = new AbortController()
    connecting = true
    NotificationApi.subscribe(onFrame, controller.signal)
      // 服务端正常关流(部署重启等)与异常失败同口径:退避重连,轮询已在断连时兜底
      .then(scheduleReconnect)
      .catch(scheduleReconnect)
  }

  const onFrame = (frame: NotificationFrame) => {
    connecting = false
    sseActive = true
    reconnectAttempt = 0
    stopPolling()
    // 心跳帧仅证连接健康;通知帧触发未读数刷新(Message.vue 徽标随 ref 自然更新)
    if (frame.type !== 'HEARTBEAT') {
      refresh()
    }
  }

  const start = () => {
    stop()
    stopped = false
    reconnectAttempt = 0
    refresh()
    // 先轮询兜底,SSE 首帧(含心跳)到达后自动挂起
    startPolling()
    connectSse()
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  const stop = () => {
    stopped = true
    stopPolling()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    closeSse()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }

  // 页面隐藏双通道都暂停(SSE 断流防后台连接堆积),回前台立刷一次并立即重连
  const onVisibilityChange = () => {
    if (document.hidden) {
      stopPolling()
      closeSse()
    } else {
      refresh()
      if (!sseActive && !connecting) {
        reconnectAttempt = 0
        connectSse()
      }
    }
  }

  return { unreadCount, refresh, start, stop }
})
