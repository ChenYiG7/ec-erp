package com.own.erp.ai.kb;

import com.own.erp.ai.config.ErpAiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : KbVectorIndex 单测(#6 RAG V1,AIR):mock EmbeddingModel 固定向量(同向量余弦=1 必命中),
 *     全程不出网;重点验证文件持久化往返 / 重建换引用 / 删除生效 / 坏文件空索引起步不炸
 */
class KbVectorIndexTest {

    @TempDir
    Path tempDir;

    private static final float[] VECTOR = {0.1f, 0.2f, 0.3f};

    private KbVectorIndex newIndex(String fileName) {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        // ⚠️ Mockito 对接口 default 方法同样整体拦截(未打桩返回 null,不执行真实实现)——
        // 入索引走 embed(Document)(SimpleVectorStore.doAdd 实测),查询走 embed(String) 两条路径都要打桩
        when(embeddingModel.embed(any(Document.class))).thenReturn(VECTOR);
        when(embeddingModel.embed(anyString())).thenReturn(VECTOR);
        ErpAiProperties props = new ErpAiProperties();
        props.getKb().setIndexPath(tempDir.resolve(fileName).toString());
        return new KbVectorIndex(embeddingModel, props);
    }

    private Document doc(String id, String text) {
        return Document.builder().id(id).text(text).metadata("title", "t").build();
    }

    @Test
    void addAndSearchRoundtrip() {
        KbVectorIndex index = newIndex("kb-a.json");
        index.addAll(List.of(doc("1", "退货规则"), doc("2", "发货时效")));
        List<Document> hits = index.search("退货规则", 2, 0.5);
        assertEquals(2, hits.size());
        assertEquals("1", hits.get(0).getId());
    }

    @Test
    void saveLoadRoundtripAcrossInstances() {
        KbVectorIndex first = newIndex("kb-b.json");
        first.addAll(List.of(doc("1", "退货规则")));
        // 新实例从同一文件加载(模拟应用重启),索引内容仍在
        KbVectorIndex second = newIndex("kb-b.json");
        assertFalse(second.needsRebuild());
        assertEquals(1, second.search("退货规则", 4, 0.5).size());
    }

    @Test
    void rebuildSwapsInstanceAtomically() {
        KbVectorIndex index = newIndex("kb-c.json");
        index.addAll(List.of(doc("stale", "旧内容")));
        int count = index.rebuild(List.of(doc("1", "新内容")));
        assertEquals(1, count);
        List<Document> hits = index.search("新内容", 4, 0.5);
        assertEquals(1, hits.size());
        assertEquals("1", hits.get(0).getId());
        assertTrue(index.needsRebuild() == false);
    }

    @Test
    void deleteIdsRemovesFromIndex() {
        KbVectorIndex index = newIndex("kb-d.json");
        index.addAll(List.of(doc("1", "退货规则"), doc("2", "发货时效")));
        index.deleteIds(List.of("1"));
        List<Document> hits = index.search("退货规则", 4, 0.0);
        assertEquals(1, hits.size());
        assertEquals("2", hits.get(0).getId());
    }

    @Test
    void corruptedFileStartsEmptyWithoutCrash() throws Exception {
        Path bad = tempDir.resolve("kb-e.json");
        java.nio.file.Files.writeString(bad, "{not-json");
        // 坏文件:空索引起步 + needsRebuild=true(待按正本重建),构造不抛
        KbVectorIndex index = newIndex("kb-e.json");
        assertTrue(index.needsRebuild());
        assertTrue(index.search("anything", 4, 0.0).isEmpty());
    }
}
