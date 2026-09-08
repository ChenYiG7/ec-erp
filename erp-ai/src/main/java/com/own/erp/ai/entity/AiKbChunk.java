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
 * @Description : AI客服知识库分块(RAG 检索语料正本;向量不入库——向量是索引派生物,
 *     SimpleVectorStore JSON 文件持久化为运行时索引,文件丢失/换 embedding 模型时按本表 chunk 文本重建)
 *     (ai_kb_chunk)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_kb_chunk")
public class AiKbChunk {

    /** 主键(=向量库文档ID的数值来源,String.valueOf(id) 作向量 doc id) */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档ID(ai_kb_document.id) */
    private Long documentId;

    /** 块序号(0 起,同文档内连续) */
    private Integer chunkIndex;

    /** 块文本(TokenTextSplitter 切分,向量化正本) */
    private String content;

    /** 块字符数 */
    private Integer charCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
