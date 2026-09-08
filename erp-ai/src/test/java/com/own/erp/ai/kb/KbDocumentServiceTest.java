package com.own.erp.ai.kb;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiKbChunk;
import com.own.erp.ai.entity.AiKbDocument;
import com.own.erp.ai.mapper.AiKbChunkMapper;
import com.own.erp.ai.mapper.AiKbDocumentMapper;
import com.own.erp.ai.request.query.AiKbDocumentQuery;
import com.own.erp.ai.response.AiKbDocumentResponse;
import com.own.erp.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : KbDocumentService 单测(#6 RAG V1,AIR):删除次序(先删向量,失败中止正本不动)/
 *     正本与分块一并删除/分页映射/详情不存在报错/重建只收 READY 文档
 */
class KbDocumentServiceTest {

    private AiKbDocumentMapper documentMapper;
    private AiKbChunkMapper chunkMapper;
    private KbVectorIndex vectorIndex;
    private KbIngestService ingestService;
    private KbDocumentService service;

    @BeforeEach
    void setUp() {
        documentMapper = mock(AiKbDocumentMapper.class);
        chunkMapper = mock(AiKbChunkMapper.class);
        vectorIndex = mock(KbVectorIndex.class);
        ingestService = mock(KbIngestService.class);
        service = new KbDocumentService(documentMapper, chunkMapper, vectorIndex, ingestService);
    }

    private AiKbDocument doc(long id, String status) {
        return AiKbDocument.builder().id(id).title("文档" + id).sourceType(AiConsts.KB_SOURCE_TEXT)
                .charCount(100).chunkCount(2).status(status).uploadedBy(7L).build();
    }

    @Test
    void deleteRemovesVectorsFirstThenCorpus() {
        when(documentMapper.selectById(1L)).thenReturn(doc(1L, AiConsts.KB_STATUS_READY));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                AiKbChunk.builder().id(11L).documentId(1L).build(),
                AiKbChunk.builder().id(12L).documentId(1L).build()));

        service.delete(1L);

        // 次序拍板:先删向量(失败即中止),后删正本
        verify(vectorIndex).deleteIds(List.of("11", "12"));
        verify(chunkMapper).delete(any());
        verify(documentMapper).deleteById(1L);
    }

    @Test
    void deleteAbortsCorpusWhenVectorDeleteFails() {
        when(documentMapper.selectById(1L)).thenReturn(doc(1L, AiConsts.KB_STATUS_READY));
        when(chunkMapper.selectList(any())).thenReturn(List.of(AiKbChunk.builder().id(11L).documentId(1L).build()));
        doThrow(new RuntimeException("索引删除失败")).when(vectorIndex).deleteIds(anyList());

        assertThrows(RuntimeException.class, () -> service.delete(1L));
        verify(chunkMapper, never()).delete(any());
        verify(documentMapper, never()).deleteById(1L);
    }

    @Test
    void deleteWithoutChunksStillRemovesDocument() {
        when(documentMapper.selectById(1L)).thenReturn(doc(1L, AiConsts.KB_STATUS_FAILED));
        when(chunkMapper.selectList(any())).thenReturn(List.of());

        service.delete(1L);

        verify(vectorIndex, never()).deleteIds(anyList());
        verify(documentMapper).deleteById(1L);
    }

    @Test
    void deleteMissingDocumentThrows() {
        when(documentMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.delete(404L));
        verify(vectorIndex, never()).deleteIds(anyList());
    }

    @Test
    void pageMapsEntitiesToResponses() {
        Page<AiKbDocument> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(doc(1L, AiConsts.KB_STATUS_READY)));
        when(documentMapper.selectPage(any(), any())).thenReturn(page);

        Page<AiKbDocumentResponse> result = service.page(new AiKbDocumentQuery());

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("文档1", result.getRecords().get(0).title());
        assertEquals(AiConsts.KB_STATUS_READY, result.getRecords().get(0).status());
    }

    @Test
    void getByIdMissingThrows() {
        when(documentMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(9L));
    }

    @Test
    void rebuildAllCollectsReadyDocsOnly() {
        when(documentMapper.selectList(any())).thenReturn(List.of(doc(1L, AiConsts.KB_STATUS_READY)));
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                AiKbChunk.builder().id(11L).documentId(1L).chunkIndex(0).content("a").build(),
                AiKbChunk.builder().id(12L).documentId(1L).chunkIndex(1).content("b").build()));
        when(ingestService.buildVectorDocument(any(AiKbChunk.class), anyString()))
                .thenAnswer(inv -> org.springframework.ai.document.Document.builder()
                        .id(String.valueOf(((AiKbChunk) inv.getArgument(0)).getId()))
                        .text(((AiKbChunk) inv.getArgument(0)).getContent()).build());

        int count = service.rebuildAll();

        assertEquals(1, count);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorIndex).rebuild(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals("11", captor.getValue().get(0).getId());
        assertNotNull(captor.getValue().get(0).getText());
        assertTrue(captor.getValue().get(1).getId().equals("12"));
    }
}
