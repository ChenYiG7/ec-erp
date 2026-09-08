import http from '@/utils/request'
import type { PageQuery, PageResult } from '@/api/interface'
import type { AiKbDocumentQuery, AiKbDocumentResponse, AiKbChunkResponse, KbTextUploadCommand } from '@/api/interface/ai/kb'

/**
 * AI 知识库(/api/ai/kb,手写页 #6 RAG V1:上传/预览非标准 CRUD,gen:page 不适用)
 * 分页差异(后端 pageNo/records vs 前端 pageNum/list)只在 page 内单点收口,禁散落页面
 */
export const aiKbApi = {
  /** 文档分页(过滤:标题模糊/状态;按 id 倒序,返回 {list,total}) */
  page: (params: AiKbDocumentQuery & PageQuery) =>
    http.get<PageResult<AiKbDocumentResponse>>('/api/ai/kb/documents', params).then(page => ({ list: page.records, total: page.total })),
  /** 文档详情 */
  detail: (id: number) => http.get<AiKbDocumentResponse>(`/api/ai/kb/documents/${id}`),
  /** 分块预览(按块序正序) */
  chunks: (id: number) => http.get<AiKbChunkResponse[]>(`/api/ai/kb/documents/${id}/chunks`),
  /** 上传文件接入(.txt/.md/.markdown,UTF-8;FormData 走 multipart,后端 admin 双闸) */
  uploadFile: (file: File, title?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    if (title) {
      formData.append('title', title)
    }
    return http.post<AiKbDocumentResponse>('/api/ai/kb/documents', formData)
  },
  /** 粘贴文本接入(标题可空,默认内容首行截断) */
  uploadText: (data: KbTextUploadCommand) => http.post<AiKbDocumentResponse>('/api/ai/kb/documents/text', data),
  /** 删除文档(先删向量后删正本) */
  remove: (id: number) => http.delete<void>(`/api/ai/kb/documents/${id}`),
  /** 重建向量索引(返回重建文档数;换 embedding 模型/索引文件丢失后使用) */
  rebuild: () => http.post<number>('/api/ai/kb/rebuild')
}
