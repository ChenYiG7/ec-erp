package com.own.erp.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : AI客服知识库文档(RAG 语料正本元数据,chunk 文本在 ai_kb_chunk)(ai_kb_document)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位;双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_kb_document")
public class AiKbDocument {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档标题(上传文件名去后缀/粘贴文本用户填写) */
    private String title;

    /** 来源:UPLOAD文件上传/TEXT粘贴文本(词表 AiConsts.KB_SOURCE_*) */
    private String sourceType;

    /** 原始文件名(仅 source_type=UPLOAD 时有值) */
    private String fileName;

    /** 原文总字符数 */
    private Integer charCount;

    /** 分块数(与 ai_kb_chunk 行数一致) */
    private Integer chunkCount;

    /** 状态:READY可检索/FAILED向量化失败(词表 AiConsts.KB_STATUS_*) */
    private String status;

    /** 上传人(sys_user.id) */
    private Long uploadedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
