/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/**
 * AI智能体(/api/ai/agents/{role},#6 chat 模板):会话/消息持久化与审计收口在后端
 * 分页差异(后端 pageNo/records vs 前端 pageNum/list)只在 pageSessions 单点收口,禁散落页面
 */
import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import { postSse } from '@/utils/sse'
import type { AgentRole, AiChatSessionQuery, AiChatSessionResponse, AiChatMessageResponse } from '@/api/interface/ai/agent'

/** 端点前缀(spec chatBase,{role} 按当前角色替换) */
const base = (role: AgentRole) => `/api/ai/agents/${role}`

export const agentApi = {
  /** 我的会话分页(最近更新倒序,返回 {list,total}) */
  pageSessions: (role, params: AiChatSessionQuery & PageQuery) =>
    http.get<PageResult<AiChatSessionResponse>>(`${base(role)}/sessions`, params).then(page => ({ list: page.records, total: page.total })),
  /** 新建会话(标题可空,后端默认"新会话",首条消息后自动回填摘要) */
  createSession: (role, title?: string) => http.post<number>(`${base(role)}/sessions`, { title }),
  /** 会话历史消息(时间正序;越权/跨源/不存在统一报"会话不存在") */
  listMessages: (role, sessionId: number) =>
    http.get<AiChatMessageResponse[]>(`${base(role)}/sessions/${sessionId}/messages`),
  /** 同步对话(SSE 不可用时的降级通道) */
  chatSync: (role, sessionId: number, message: string) =>
    http.post<string>(`${base(role)}/sessions/${sessionId}/chat-sync`, { message }),
  /** 流式对话(SSE):逐 TextBlock delta 回调;帧解析/鉴权/错误收口 utils/sse(postSse) */
  chatStream: (role, sessionId: number, message: string, onChunk: (text: string) => void): Promise<void> =>
    postSse(`${base(role)}/sessions/${sessionId}/chat`, { message }, onChunk)
}
