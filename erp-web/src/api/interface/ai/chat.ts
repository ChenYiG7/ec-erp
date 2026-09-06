/**
 * AI 助手类型(对齐 tools/openapi.json 快照的 AiChatSession / AiChatMessage / ChatSendCommand 系 schema,禁手抄漂移)
 * 手写页 #6:chat 域非 CRUD 范式(SSE 流式会话),gen:page 不适用,类型手对快照
 */

/** AI 会话(schema: AiChatSessionResponse;仅本人会话,最近更新倒序) */
export interface AiChatSessionResponse {
  id: number
  userId: number
  /** 会话标题(首条消息截断回填,默认"新会话") */
  title: string
  createdAt: string
  updatedAt: string
}

/** 会话分页入参(schema: AiChatSessionQuery;userId 服务端强制本人,前端不传) */
export interface AiChatSessionQuery {
  userId?: number
}

/** AI 消息(schema: AiChatMessageResponse;role 词表 USER/AI/TOOL,TOOL 行后端暂不落,前端防御渲染) */
export interface AiChatMessageResponse {
  id: number
  sessionId: number
  role: 'USER' | 'AI' | 'TOOL' | string
  content: string
  /** 工具名(role=TOOL 时审计用) */
  toolName?: string
  promptTokens?: number
  completionTokens?: number
  createdAt: string
}

/** 发送命令(schema: ChatSendCommand) */
export interface ChatSendCommand {
  message: string
}

/** 页面 UI 消息 = 契约消息 + 流式占位扩展(占位行 id 为负数,不与库内 id 混淆) */
export interface ChatUIMessage extends AiChatMessageResponse {
  /** 流式进行中:气泡尾部显示光标,禁止复制误导 */
  streaming?: boolean
}
