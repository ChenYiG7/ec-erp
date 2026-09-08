package com.own.erp.ai.kb;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiKbChunk;
import com.own.erp.ai.entity.AiKbDocument;
import com.own.erp.ai.mapper.AiKbChunkMapper;
import com.own.erp.ai.mapper.AiKbDocumentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库索引初始化器(#6 RAG V1):ApplicationReady 后一次性判定——
 *     索引文件已加载则跳过;库内 READY 文档无 chunk 则跳过;无 AI key 则跳过(告警留痕,配置后重启或手动重建);
 *     否则按 ai_kb_chunk 正本全量重嵌入重建(与重建端点同一条路径:KbIngestService.buildVectorDocuments)。
 *     全程 try/catch 不抛——索引缺失只降级(检索空结果),绝不阻断应用启动
 */
@Component
@Slf4j
public class KbIndexInitializer {

    private final KbVectorIndex vectorIndex;
    private final AiKbDocumentMapper documentMapper;
    private final AiKbChunkMapper chunkMapper;
    private final KbIngestService ingestService;

    /** 模型 api-key 原值(仅判空决定是否可重建,不落日志/返回体——docs/07 §7 凭证纪律) */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public KbIndexInitializer(KbVectorIndex vectorIndex,
                              AiKbDocumentMapper documentMapper,
                              AiKbChunkMapper chunkMapper,
                              KbIngestService ingestService) {
        this.vectorIndex = vectorIndex;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.ingestService = ingestService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            if (!vectorIndex.needsRebuild()) {
                return;
            }
            List<Long> readyDocIds = documentMapper.selectList(
                            new LambdaQueryWrapper<AiKbDocument>().eq(AiKbDocument::getStatus, AiConsts.KB_STATUS_READY))
                    .stream().map(AiKbDocument::getId).toList();
            if (readyDocIds.isEmpty()) {
                log.info("知识库索引文件缺失但无 READY 文档,跳过重建");
                return;
            }
            if (StrUtil.isBlank(apiKey)) {
                log.warn("知识库索引文件缺失且 AI 未配置(OPENAI_API_KEY/AI_API_KEY),跳过自动重建——"
                        + "配置后重启或在知识库页面手动重建");
                return;
            }
            List<AiKbChunk> chunks = chunkMapper.selectList(
                    new LambdaQueryWrapper<AiKbChunk>().in(AiKbChunk::getDocumentId, readyDocIds));
            // 标题仅作向量元数据回显:一次装载 docId→title 映射,禁逐 chunk 查库(N+1)
            Map<Long, String> titleById = documentMapper.selectBatchIds(readyDocIds).stream()
                    .collect(java.util.stream.Collectors.toMap(AiKbDocument::getId,
                            doc -> StrUtil.nullToEmpty(doc.getTitle())));
            List<Document> documents = new ArrayList<>(chunks.size());
            for (AiKbChunk chunk : chunks) {
                documents.add(ingestService.buildVectorDocument(chunk,
                        titleById.getOrDefault(chunk.getDocumentId(), "")));
            }
            int count = vectorIndex.rebuild(documents);
            log.info("知识库向量索引已按正本重建:文档 {} 个 / 分块 {} 条", readyDocIds.size(), count);
        } catch (Exception e) {
            // 重建失败只降级:检索空结果,不阻断启动
            log.warn("知识库向量索引自动重建失败(检索将降级为空结果,可稍后手动重建):{}", e.getMessage());
        }
    }
}
