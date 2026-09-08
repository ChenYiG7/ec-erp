import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import { postSse } from '@/utils/sse'
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
   * 帧解析/鉴权/错误收口 utils/sse(postSse,#6 agent 域接入时沉淀复用);
   * 错误/帧形态差异随 #3 联调校准 TODO(#6)
   */
  chatStream: async (sessionId: number, message: string, onChunk: (text: string) => void): Promise<void> =>
    postSse(`/api/ai/chat/sessions/${sessionId}/chat`, { message }, onChunk)
}
