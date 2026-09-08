package com.own.erp.ai.kb;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiKbChunk;
import com.own.erp.ai.entity.AiKbDocument;
import com.own.erp.ai.mapper.AiKbChunkMapper;
import com.own.erp.ai.mapper.AiKbDocumentMapper;
import com.own.erp.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : KbIngestService 单测(#6 RAG V1,AIR:mock Mapper/向量索引,全程不出网):
 *     正常接入(doc FAILED→READY/chunk 逐条入库/向量 doc id=chunkId)/无 key 拒绝/空内容拒绝/
 *     超长拒绝/向量化失败留 FAILED 正本不抛/默认标题首行截断
 */
class KbIngestServiceTest {

    private static final Long USER_ID = 7L;
    private static final String TEXT = "这是一篇用于测试的知识文档,内容超过最小分块长度,应被切成一个分块。";

    private AiKbDocumentMapper documentMapper;
    private AiKbChunkMapper chunkMapper;
    private KbVectorIndex vectorIndex;
    private KbIngestService service;

    @BeforeEach
    void setUp() {
        documentMapper = mock(AiKbDocumentMapper.class);
        chunkMapper = mock(AiKbChunkMapper.class);
        vectorIndex = mock(KbVectorIndex.class);
        service = new KbIngestService(documentMapper, chunkMapper, vectorIndex, new ErpAiProperties());
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        // MP insert/updateById 存在 T 与 Collection<T> 双 overload,匹配器须显式类型消歧
        doAnswer(inv -> {
            AiKbDocument doc = inv.getArgument(0);
            doc.setId(101L);
            return 1;
        }).when(documentMapper).insert(any(AiKbDocument.class));
        doAnswer(inv -> {
            AiKbChunk chunk = inv.getArgument(0);
            chunk.setId(200L + chunk.getChunkIndex() + 1);
            return 1;
        }).when(chunkMapper).insert(any(AiKbChunk.class));
    }

    @Test
    void ingestPersistsCorpusAndVectorsReady() {
        AiKbDocument doc = service.ingest("退货规则", AiConsts.KB_SOURCE_TEXT, null, TEXT, USER_ID);

        assertEquals(AiConsts.KB_STATUS_READY, doc.getStatus());
        assertEquals(101L, doc.getId());
        assertEquals(TEXT.length(), doc.getCharCount());
        // insert/updateById 传同一实体引用,setStatus 会污染已存引用——前置 FAILED 态无法按值断言,
        // 只验调用发生 + 最终 READY(FAILED 中间态语义由 ingestKeepsFailedCorpusWhenVectorizationFails 覆盖)
        verify(documentMapper).insert(any(AiKbDocument.class));
        verify(documentMapper).updateById(argThat((AiKbDocument d) -> AiConsts.KB_STATUS_READY.equals(d.getStatus())));
        // TokenTextSplitter 对短文本产 1 个分块(实测 35 字中文 → 1 块);chunk 入库 + 向量 doc id = chunkId
        verify(chunkMapper).insert(argThat((AiKbChunk c) -> c.getDocumentId().equals(101L) && c.getChunkIndex() == 0));
        org.mockito.ArgumentCaptor<List<Document>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorIndex).addAll(captor.capture());
        List<Document> docs = captor.getValue();
        assertEquals(1, docs.size());
        assertEquals("201", docs.get(0).getId());
        assertEquals("101", docs.get(0).getMetadata().get(AiConsts.KB_META_DOCUMENT_ID));
    }

    @Test
    void ingestRejectsWhenApiKeyMissing() {
        ReflectionTestUtils.setField(service, "apiKey", " ");
        assertThrows(BusinessException.class,
                () -> service.ingest(null, AiConsts.KB_SOURCE_TEXT, null, TEXT, USER_ID));
        verify(documentMapper, never()).insert(any(AiKbDocument.class));
        verifyNoVectorInteractions();
    }

    @Test
    void ingestRejectsBlankContent() {
        assertThrows(BusinessException.class,
                () -> service.ingest(null, AiConsts.KB_SOURCE_TEXT, null, "   ", USER_ID));
        verifyNoVectorInteractions();
    }

    @Test
    void ingestRejectsOverlongContent() {
        ErpAiProperties props = new ErpAiProperties();
        props.getKb().setMaxDocumentChars(10);
        service = new KbIngestService(documentMapper, chunkMapper, vectorIndex, props);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        assertThrows(BusinessException.class,
                () -> service.ingest(null, AiConsts.KB_SOURCE_TEXT, null, TEXT, USER_ID));
        verifyNoVectorInteractions();
    }

    @Test
    void ingestKeepsFailedCorpusWhenVectorizationFails() {
        doThrow(new RuntimeException("embedding 超时")).when(vectorIndex).addAll(anyList());

        AiKbDocument doc = service.ingest(null, AiConsts.KB_SOURCE_TEXT, null, TEXT, USER_ID);

        // 拍板:向量化失败不抛不回滚——正本与分块留存为 FAILED,修复后可重建
        assertEquals(AiConsts.KB_STATUS_FAILED, doc.getStatus());
        verify(documentMapper, never()).updateById(any(AiKbDocument.class));
        verify(chunkMapper).insert(any(AiKbChunk.class));
    }

    @Test
    void defaultTitleFromFirstLineTruncated() {
        String longFirstLine = "这个首行特别长会超过标题上限需要被截断处理一二三四五六七八九十甲乙丙丁戊己庚辛壬癸子丑寅卯";
        AiKbDocument doc = service.ingest(null, AiConsts.KB_SOURCE_TEXT, null, longFirstLine + "\n第二行", USER_ID);
        assertEquals(AiConsts.TITLE_MAX_LEN, doc.getTitle().length());
        assertTrue(doc.getCharCount() > doc.getTitle().length());
    }

    @Test
    void buildVectorDocumentMapsChunkIdAndMetadata() {
        AiKbChunk chunk = AiKbChunk.builder().id(9L).documentId(3L).chunkIndex(0).content("内容").build();
        Document document = service.buildVectorDocument(chunk, "标题");
        assertEquals("9", document.getId());
        assertEquals("内容", document.getText());
        assertEquals("3", document.getMetadata().get(AiConsts.KB_META_DOCUMENT_ID));
        assertEquals("标题", document.getMetadata().get(AiConsts.KB_META_TITLE));
    }

    private void verifyNoVectorInteractions() {
        verify(chunkMapper, never()).insert(any(AiKbChunk.class));
        org.mockito.Mockito.verifyNoInteractions(vectorIndex);
    }
}
