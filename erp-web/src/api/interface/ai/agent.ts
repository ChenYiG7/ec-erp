/**
 * 本文件由 pnpm gen:page 生成(spec 行式拍板 + tools/openapi.json 快照)
 * 默认存在即跳过:人工改动不会被 --force 之外的任何方式覆盖;重新生成前先 diff 人工改动
 * 框架代码禁手改;业务槽位一律 TODO(编号),编号已登记 TODO.md
 */

/**
 * AI智能体类型(chat 模板,#6):契约类型单一来源复用 @/api/interface/ai/chat
 */

/** 会话页角色词表(role 不落会话——会话不绑角色,同一会话可跨角色续聊) */
export type AgentRole = 'SUPPORT' | 'OPS'

export type { AiChatSessionQuery, AiChatSessionResponse, AiChatMessageResponse, ChatSendCommand, ChatUIMessage } from '@/api/interface/ai/chat'
