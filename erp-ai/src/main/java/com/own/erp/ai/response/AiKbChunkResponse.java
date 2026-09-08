package com.own.erp.ai.response;

import com.own.erp.ai.entity.AiKbChunk;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库分块对外结构(预览用;record+@Builder,from 显式逐字段映射禁反射拷贝)
 */
@Builder
public record AiKbChunkResponse(

        /** 主键(=向量库文档ID数值来源) */
        Long id,

        /** 所属文档ID */
        Long documentId,

        /** 块序号(0 起) */
        Integer chunkIndex,

        /** 块文本(TokenTextSplitter 切分) */
        String content,

        /** 块字符数 */
        Integer charCount

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝);预览截断由 Controller/前端层做,结构出全量文本 */
    public static AiKbChunkResponse from(AiKbChunk entity) {
        return AiKbChunkResponse.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .chunkIndex(entity.getChunkIndex())
                .content(entity.getContent())
                .charCount(entity.getCharCount())
                .build();
    }
}
