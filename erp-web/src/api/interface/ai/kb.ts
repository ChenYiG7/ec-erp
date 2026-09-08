/**
 * AI 知识库类型(对齐 KbController 出入参 schema,#6 RAG V1;禁手抄漂移)
 */

/** 知识库文档(schema: AiKbDocumentResponse) */
export interface AiKbDocumentResponse {
  id: number
  /** 文档标题(上传文件名去后缀/粘贴文本首行截断) */
  title: string
  /** 来源:UPLOAD文件上传/TEXT粘贴文本 */
  sourceType: 'UPLOAD' | 'TEXT' | string
  /** 原始文件名(仅 UPLOAD) */
  fileName?: string
  /** 原文总字符数 */
  charCount: number
  /** 分块数 */
  chunkCount: number
  /** 状态:READY可检索/FAILED向量化失败(可重建) */
  status: 'READY' | 'FAILED' | string
  /** 上传人(sys_user.id) */
  uploadedBy: number
  createdAt: string
  updatedAt: string
}

/** 知识库分块(schema: AiKbChunkResponse;预览用) */
export interface AiKbChunkResponse {
  id: number
  documentId: number
  /** 块序号(0 起) */
  chunkIndex: number
  /** 块文本(TokenTextSplitter 切分) */
  content: string
  charCount: number
}

/** 分页查询入参(schema: AiKbDocumentQuery) */
export interface AiKbDocumentQuery {
  /** 标题模糊过滤 */
  title?: string
  /** 状态过滤:READY/FAILED */
  status?: string
}

/** 粘贴文本接入入参(schema: KbTextUploadCommand) */
export interface KbTextUploadCommand {
  title?: string
  content: string
}
