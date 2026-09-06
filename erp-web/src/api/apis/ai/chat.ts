import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import { useUserStore } from '@/stores/modules/user'
import type { AiChatSessionQuery, AiChatSessionResponse, AiChatMessageResponse } from '@/api/interface/ai/chat'

/**
 * AI 助手(/api/ai/chat,手写页 #6:chat 域非 CRUD 范式,gen:page 不适用)
 * 分页差异(后端 pageNo/records vs 前端 pageNum/list)只在 pageSessions 内单点收口,禁散落页面
 */
export const aiChatApi = {
  /** 我的会话分页(最近更新倒序,返回 {list,total}) */
  pageSessions: (params: AiChatSessionQuery & PageQuery) =>
    http.get<PageResult<AiChatSessionResponse>>('/api/ai/chat/sessions', params).then(page => ({ list: page.records, total: page.total })),
  /** 新建会话(标题可空,后端默认"新会话",首条消息后自动回填摘要) */
  createSession: (title?: string) => http.post<number>('/api/ai/chat/sessions', { title }),
  /** 会话历史消息(时间正序;越权/不存在统一报"会话不存在") */
  listMessages: (sessionId: number) => http.get<AiChatMessageResponse[]>(`/api/ai/chat/sessions/${sessionId}/messages`),
  /** 同步对话(一次性完整回复,带工具查询耗时高于流式首包;SSE 不可用时的降级通道) */
  chatSync: (sessionId: number, message: string) => http.post<string>(`/api/ai/chat/sessions/${sessionId}/chat-sync`, { message }),

  /**
   * 流式对话(SSE):POST + Authorization 用 fetch 手解(EventSource 仅支持 GET,axios 管不到流)。
   * 帧格式按 openapi 描述"text/event-stream,逐段返回"实现:裸文本 data: 块逐段回调,
   * 兼容 OpenAI 风格 [DONE] 结束标记;错误/帧形态差异随 #3 联调校准 TODO(#6)。
   * 鉴权与错误在此单点收口(不经 axios 拦截器):非 2xx 解析 Result.msg 直抛
   */
  chatStream: async (sessionId: number, message: string, onChunk: (text: string) => void): Promise<void> => {
    const token = useUserStore().getUserToken()
    const res = await fetch(`/api/ai/chat/sessions/${sessionId}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ message })
    })
    if (!res.ok || !res.body) {
      // 错误体仍是 Result JSON(HTTP 200+code≠200 形态在这里表现为非 2xx 流失败);401 由登录态自然过期兜底
      const body = await res.json().catch(() => null)
      throw new Error((body as { msg?: string } | null)?.msg ?? `流式请求失败(${res.status})`)
    }

    const handleEvent = (block: string) => {
      // SSE 事件块:取 data: 行(剥一个前导空格),多行以 \n 连接;[DONE]/空块跳过
      const payload = block
        .split(/\r?\n/)
        .filter(line => line.startsWith('data:'))
        .map(line => line.slice(5).replace(/^ /, ''))
        .join('\n')
      if (payload && payload !== '[DONE]') {
        onChunk(payload)
      }
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() ?? ''
      blocks.forEach(handleEvent)
    }
    // 流异常截断时的残尾兜底(无结束分隔符的最后一块)
    if (buffer.trim()) {
      handleEvent(buffer)
    }
  }
}
