import { useUserStore } from '@/stores/modules/user'

/**
 * POST + Authorization 的 SSE 流式读取(#6 chat/agent 两域共用):EventSource 仅支持 GET、axios 管不到流,
 * 用 fetch 手解。帧格式按后端 openapi 描述"text/event-stream,裸文本 data: 块逐段"实现,
 * 兼容 OpenAI 风格 [DONE] 结束标记;鉴权与错误在此单点收口(不经 axios 拦截器):非 2xx 解析 Result.msg 直抛
 */
export const postSse = async (url: string, body: unknown, onChunk: (text: string) => void): Promise<void> => {
  const token = useUserStore().getUserToken()
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(body)
  })
  if (!res.ok || !res.body) {
    // 错误体仍是 Result JSON(HTTP 200+code≠200 形态在这里表现为非 2xx 流失败);401 由登录态自然过期兜底
    const err = await res.json().catch(() => null)
    throw new Error((err as { msg?: string } | null)?.msg ?? `流式请求失败(${res.status})`)
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
