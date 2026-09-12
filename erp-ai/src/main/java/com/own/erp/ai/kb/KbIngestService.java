package com.own.erp.ai.kb;

import cn.hutool.core.util.StrUtil;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiKbChunk;
import com.own.erp.ai.entity.AiKbDocument;
import com.own.erp.ai.mapper.AiKbChunkMapper;
import com.own.erp.ai.mapper.AiKbDocumentMapper;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.common.oss.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库接入服务(#6 AI 客服 RAG V1):
 *     文本 → TokenTextSplitter 切块 → ai_kb_document/ai_kb_chunk 落库(chunk 文本=重建正本)
 *     → SimpleVectorStore 向量化入索引(EmbeddingModel 与 chat 同连接,spring.ai.openai.* 单源)。
 *     降级语义(拍板):无 AI key 直接拒绝(不产垃圾文档);向量化调用失败 → 文档与分块留存,
 *     status=FAILED(修复后走重建索引转 READY),不抛异常、不回滚正本——文本是资产,向量只是派生索引。
 *     原文存档(#25 OSS,2026-09-11):UPLOAD 上传的原始字节同步存 OSS(kb/{documentId}/{文件名}),
 *     key/字节数回填 ai_kb_document 两列;存档是访问加速非 RAG 依赖——OSS 未启用走原链路静默跳过,
 *     存档异常 log.warn 不中断(chunk 正本仍在,原文可随 OSS 修复后重传,拍板同向量化降级口径)。
 *     检索注入面在 KbSearchService;删除/重建面在 KbDocumentService
 */
@Service
@Slf4j
public class KbIngestService {

    private final AiKbDocumentMapper documentMapper;
    private final AiKbChunkMapper chunkMapper;
    private final KbVectorIndex vectorIndex;
    private final ErpAiProperties props;
    private final OssService ossService;

    /** 模型 api-key 原值(仅判空作"AI 未配置"友好拦截,不落日志/返回体——docs/07 §7 凭证纪律) */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public KbIngestService(AiKbDocumentMapper documentMapper,
                           AiKbChunkMapper chunkMapper,
                           KbVectorIndex vectorIndex,
                           ErpAiProperties props,
                           OssService ossService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.vectorIndex = vectorIndex;
        this.props = props;
        this.ossService = ossService;
    }

    /**
     * 接入一篇文档:切块落库 + 向量化入索引;返回落库后的文档实体(看 status 判断向量是否就绪)。
     * 事务口径(拍板,V1):各插入自动提交,不做包 @Transactional——embedding 外呼禁占事务连接;
     * 正本插入中途失败的窗口极小(单文档管理员交互),残留表现为列表可见的 FAILED 文档(可删除自愈),
     * 不引入补偿逻辑(先保持简单,同 #7 逻辑删除缓做口径)
     */
    public AiKbDocument ingest(String title, String sourceType, String fileName, String text, Long userId) {
        return ingest(title, sourceType, fileName, text, userId, null);
    }

    /** 带原文存档的接入:originalBytes 非空(UPLOAD 链路)时同步存 OSS,粘贴文本传 null */
    public AiKbDocument ingest(String title, String sourceType, String fileName, String text,
                               Long userId, byte[] originalBytes) {
        requireConfigured();
        String content = StrUtil.trim(text);
        if (StrUtil.isBlank(content)) {
            throw new BusinessException("文档内容不能为空");
        }
        if (content.length() > props.getKb().getMaxDocumentChars()) {
            throw new BusinessException(StrUtil.format("文档内容超过上限({}/{} 字符),请拆分后上传",
                    content.length(), props.getKb().getMaxDocumentChars()));
        }
        String docTitle = StrUtil.isBlank(title) ? defaultTitle(content) : StrUtil.trim(title);

        // 切块(纯内存,先于 DB;切不出块视为空内容拒绝;chunkSize 走配置,2.0.1 无参构造已废弃统一走 builder)
        List<Document> chunkDocs = new TokenTextSplitter()
                .builder()
                .withChunkSize(props.getKb().getChunkSize())
                .build()
                .apply(List.of(new Document(content)));
        if (chunkDocs.isEmpty()) {
            throw new BusinessException("文档内容切分后无有效分块");
        }

        // 正本落库:doc 先 FAILED 占位,chunk 逐条入库拿 id(=向量 doc id 来源)
        AiKbDocument doc = AiKbDocument.builder()
                .title(docTitle)
                .sourceType(sourceType)
                .fileName(fileName)
                .charCount(content.length())
                .chunkCount(chunkDocs.size())
                .status(AiConsts.KB_STATUS_FAILED)
                .uploadedBy(userId)
                .build();
        documentMapper.insert(doc);
        archiveOriginal(doc, fileName, originalBytes);
        List<Document> vectorDocs = new ArrayList<>(chunkDocs.size());
        for (int i = 0; i < chunkDocs.size(); i++) {
            AiKbChunk chunk = AiKbChunk.builder()
                    .documentId(doc.getId())
                    .chunkIndex(i)
                    .content(chunkDocs.get(i).getText())
                    .charCount(chunkDocs.get(i).getText().length())
                    .build();
            chunkMapper.insert(chunk);
            vectorDocs.add(buildVectorDocument(chunk, docTitle));
        }

        // 向量化入索引:失败保留 FAILED 正本(修复后可重建),不抛不回滚
        try {
            vectorIndex.addAll(vectorDocs);
            doc.setStatus(AiConsts.KB_STATUS_READY);
            documentMapper.updateById(doc);
            log.info("知识库文档已接入:docId={} title={} chunks={}", doc.getId(), docTitle, chunkDocs.size());
        } catch (Exception e) {
            log.warn("知识库文档向量化失败(正本已留存为 FAILED,可重建索引):docId={} {}", doc.getId(), e.getMessage());
        }
        return doc;
    }

    /**
     * 原文存档(#25):UPLOAD 链路传原始字节;成功回填 key/size 两列。
     * 未启用(OssService 抛"未启用"业务异常)静默走原链路;其余异常 log.warn 不中断接入——
     * 原文库是访问加速非 RAG 依赖,chunk 文本仍可重建(拍板同向量化降级口径)
     */
    private void archiveOriginal(AiKbDocument doc, String fileName, byte[] originalBytes) {
        if (originalBytes == null || originalBytes.length == 0 || StrUtil.isBlank(fileName)) {
            return;
        }
        try {
            String key = ossService.kbKey(doc.getId(), fileName);
            ossService.upload(key, originalBytes, "application/octet-stream");
            doc.setOriginalFileKey(key);
            doc.setOriginalFileSize((long) originalBytes.length);
        } catch (BusinessException e) {
            log.info("OSS 未启用,知识库原文跳过存档(RAG 链路不受影响):docId={}", doc.getId());
        } catch (Exception e) {
            log.warn("知识库原文 OSS 存档失败(chunk 正本仍在,原文访问降级):docId={} {}",
                    doc.getId(), e.getMessage());
        }
    }

    /** chunk 正本 → 向量库 Document(重建/接入共用同一条构造,口径单一);id=chunkId 字符串,删除按此对齐 */    public Document buildVectorDocument(AiKbChunk chunk, String title) {
        return Document.builder()
                .id(String.valueOf(chunk.getId()))
                .text(chunk.getContent())
                .metadata(Map.of(
                        AiConsts.KB_META_DOCUMENT_ID, String.valueOf(chunk.getDocumentId()),
                        AiConsts.KB_META_TITLE, StrUtil.nullToEmpty(title)))
                .build();
    }

    /** 粘贴文本默认标题:首行截断(同会话标题口径,DB VARCHAR(128) 内) */
    private String defaultTitle(String content) {
        String firstLine = content.split("\n", 2)[0].trim();
        String title = StrUtil.isBlank(firstLine) ? "粘贴文本" : firstLine;
        return title.length() > AiConsts.TITLE_MAX_LEN ? title.substring(0, AiConsts.TITLE_MAX_LEN) : title;
    }

    private void requireConfigured() {
        if (StrUtil.isBlank(apiKey)) {
            throw new BusinessException("AI 能力未配置:请设置环境变量 OPENAI_API_KEY(或 local.properties 同名键)后重启");
        }
    }
}
