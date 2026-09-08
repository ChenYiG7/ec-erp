package com.own.erp.ai.response;

import com.own.erp.ai.entity.AiKbDocument;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库文档对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 显式逐字段映射禁反射拷贝
 */
@Builder
public record AiKbDocumentResponse(

        /** 主键 */
        Long id,

        /** 文档标题 */
        String title,

        /** 来源:UPLOAD文件上传/TEXT粘贴文本 */
        String sourceType,

        /** 原始文件名(仅 UPLOAD) */
        String fileName,

        /** 原文总字符数 */
        Integer charCount,

        /** 分块数 */
        Integer chunkCount,

        /** 状态:READY可检索/FAILED向量化失败 */
        String status,

        /** 上传人(sys_user.id) */
        Long uploadedBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static AiKbDocumentResponse from(AiKbDocument entity) {
        return AiKbDocumentResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .sourceType(entity.getSourceType())
                .fileName(entity.getFileName())
                .charCount(entity.getCharCount())
                .chunkCount(entity.getChunkCount())
                .status(entity.getStatus())
                .uploadedBy(entity.getUploadedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
