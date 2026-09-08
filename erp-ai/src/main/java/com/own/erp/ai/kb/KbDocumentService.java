package com.own.erp.ai.kb;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiKbChunk;
import com.own.erp.ai.entity.AiKbDocument;
import com.own.erp.ai.mapper.AiKbChunkMapper;
import com.own.erp.ai.mapper.AiKbDocumentMapper;
import com.own.erp.ai.request.query.AiKbDocumentQuery;
import com.own.erp.ai.response.AiKbChunkResponse;
import com.own.erp.ai.response.AiKbDocumentResponse;
import com.own.erp.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库文档管理服务(#6 RAG V1):分页/详情/分块预览/删除/全量重建。
 *     删除次序(拍板):先删向量索引(失败即中止,DB 不动,防"正本已删而向量残留"脏检索),
 *     后删 DB 正本(事务);索引文件持久化由 KbVectorIndex 内部尽力而为。
 *     重建 = 清空索引按 ai_kb_chunk 正本(READY 文档)重嵌入,换 embedding 模型/索引文件丢失后手动触发
 */
@Service
@Slf4j
public class KbDocumentService {

    private final AiKbDocumentMapper documentMapper;
    private final AiKbChunkMapper chunkMapper;
    private final KbVectorIndex vectorIndex;
    private final KbIngestService ingestService;

    public KbDocumentService(AiKbDocumentMapper documentMapper,
                             AiKbChunkMapper chunkMapper,
                             KbVectorIndex vectorIndex,
                             KbIngestService ingestService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.vectorIndex = vectorIndex;
        this.ingestService = ingestService;
    }

    /** 文档分页(过滤:标题模糊/状态;按 id 倒序) */
    public Page<AiKbDocumentResponse> page(AiKbDocumentQuery query) {
        LambdaQueryWrapper<AiKbDocument> wrapper = new LambdaQueryWrapper<AiKbDocument>()
                .like(StrUtil.isNotBlank(query.getTitle()), AiKbDocument::getTitle, query.getTitle())
                .eq(StrUtil.isNotBlank(query.getStatus()), AiKbDocument::getStatus, query.getStatus())
                .orderByDesc(AiKbDocument::getId);
        Page<AiKbDocument> result =
                documentMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()), wrapper);
        Page<AiKbDocumentResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(AiKbDocumentResponse::from).toList());
        return responsePage;
    }

    /** 文档详情;不存在报错(管理面无归属维度,登录即可见) */
    public AiKbDocumentResponse getById(Long id) {
        AiKbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        return AiKbDocumentResponse.from(doc);
    }

    /** 分块预览(按块序正序;单文档分块量级有限直接全量出,截断展示归前端) */
    public List<AiKbChunkResponse> listChunks(Long documentId) {
        requireExists(documentId);
        return chunkMapper.selectList(new LambdaQueryWrapper<AiKbChunk>()
                        .eq(AiKbChunk::getDocumentId, documentId)
                        .orderByAsc(AiKbChunk::getChunkIndex))
                .stream().map(AiKbChunkResponse::from).toList();
    }

    /**
     * 删除文档:先删向量(失败即中止)后删正本。
     * 整体 @Transactional:向量删除是内存+文件 IO 非外呼(与 ingest 的 embedding 外呼不同),占连接可忽略;
     * 删向量失败抛异常 → 事务内尚无 DB 变更,正本不动可重试。
     * 查 chunk 不用 .select(SFunction) 投影——select 急切解析 MP 元数据,纯单测环境炸(同 #14 set 坑先例),
     * 单文档分块量级有限,全列查询无碍
     */
    @Transactional
    public void delete(Long id) {
        AiKbDocument doc = requireExists(id);
        List<Long> chunkIds = chunkMapper.selectList(new LambdaQueryWrapper<AiKbChunk>()
                        .eq(AiKbChunk::getDocumentId, id))
                .stream().map(AiKbChunk::getId).toList();
        if (!chunkIds.isEmpty()) {
            vectorIndex.deleteIds(chunkIds.stream().map(String::valueOf).toList());
        }
        chunkMapper.delete(new LambdaQueryWrapper<AiKbChunk>().eq(AiKbChunk::getDocumentId, id));
        documentMapper.deleteById(id);
        log.info("知识库文档已删除:docId={} title={} chunks={}", id, doc.getTitle(), chunkIds.size());
    }

    /** 全量重建(手动端点/索引文件丢失自动重建共用):只重建 READY 文档,FAILED 本就不在索引 */
    public int rebuildAll() {
        List<AiKbDocument> readyDocs = documentMapper.selectList(
                new LambdaQueryWrapper<AiKbDocument>().eq(AiKbDocument::getStatus, AiConsts.KB_STATUS_READY));
        List<Document> documents = new java.util.ArrayList<>();
        for (AiKbDocument doc : readyDocs) {
            List<AiKbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiKbChunk>()
                    .eq(AiKbChunk::getDocumentId, doc.getId()).orderByAsc(AiKbChunk::getChunkIndex));
            for (AiKbChunk chunk : chunks) {
                documents.add(ingestService.buildVectorDocument(chunk, StrUtil.nullToEmpty(doc.getTitle())));
            }
        }
        int count = vectorIndex.rebuild(documents);
        log.info("知识库向量索引全量重建完成:文档 {} 个 / 分块 {} 条", readyDocs.size(), count);
        return readyDocs.size();
    }

    private AiKbDocument requireExists(Long id) {
        AiKbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        return doc;
    }
}
