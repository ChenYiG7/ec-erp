package com.own.erp.ai.kb;

import com.own.erp.ai.config.ErpAiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库向量索引封装(#6 AI 客服 RAG V1):
 *     向量库拍板 = Spring AI SimpleVectorStore(JSON 文件持久化,零新基建)——构造时加载索引文件,
 *     文件缺失/损坏时以空索引起步(启动不炸,由 KbIndexInitializer 按正本重建);
 *     重建采用"新建实例整体换引用"而非清空(map 无公开 clear 口,换引用天然原子);
 *     所有操作以内部锁串行(SimpleVectorStore 内部为非并发 LinkedHashMap,chat 并发检索必须互斥);
 *     save 尽力而为(内存态永远正确,文件暂旧无碍,下次变更再落)。
 *     VectorStore 接口语义保持:后续换 pgvector/Redis 只换本类实现,业务面不动
 */
@Component
@Slf4j
public class KbVectorIndex {

    private final EmbeddingModel embeddingModel;
    private final ErpAiProperties props;
    private final Object lock = new Object();

    private SimpleVectorStore store;

    /** 构造时是否成功从索引文件加载(false = 空索引起步,待重建) */
    private boolean loadedFromFile;

    public KbVectorIndex(EmbeddingModel embeddingModel, ErpAiProperties props) {
        this.embeddingModel = embeddingModel;
        this.props = props;
        this.store = SimpleVectorStore.builder(embeddingModel).build();
        File file = indexFile();
        if (file.exists()) {
            try {
                this.store.load(file);
                this.loadedFromFile = true;
                log.info("知识库向量索引已从文件加载:{}", file.getAbsolutePath());
            } catch (Exception e) {
                // 坏文件不炸启动:空索引起步,Initializer 会按 ai_kb_chunk 正本重建
                log.warn("知识库向量索引文件加载失败,以空索引起步(待按正本重建):{}", e.getMessage());
                this.store = SimpleVectorStore.builder(embeddingModel).build();
            }
        }
    }

    /** 批量入索引并持久化(向量化在 SimpleVectorStore.add 内部经 EmbeddingModel 完成) */
    public void addAll(List<Document> documents) {
        synchronized (lock) {
            store.add(documents);
            persistLocked();
        }
    }

    /** 按向量 doc id(= ai_kb_chunk.id 字符串)删除并持久化 */
    public void deleteIds(List<String> ids) {
        synchronized (lock) {
            store.delete(ids);
            persistLocked();
        }
    }

    /** 相似检索(top-k + 相似度下限过滤在 SearchRequest 内完成);失败降级空列表,检索异常不阻断调用方 */
    public List<Document> search(String query, int topK, double minScore) {
        try {
            synchronized (lock) {
                return store.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(minScore)
                        .build());
            }
        } catch (Exception e) {
            log.warn("知识库向量检索失败,降级为无检索结果:{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 全量重建:新建空实例逐条重嵌入后整体换引用(原子);返回重建文档数 */
    public int rebuild(List<Document> documents) {
        synchronized (lock) {
            SimpleVectorStore fresh = SimpleVectorStore.builder(embeddingModel).build();
            if (!documents.isEmpty()) {
                fresh.add(documents);
            }
            this.store = fresh;
            this.loadedFromFile = true;
            persistLocked();
            return documents.size();
        }
    }

    /** 是否需要按正本重建(索引文件缺失或损坏) */
    public boolean needsRebuild() {
        return !loadedFromFile;
    }

    /** 手动持久化(重建端点收尾用;add/delete/rebuild 内已自动持久化) */
    public void persist() {
        synchronized (lock) {
            persistLocked();
        }
    }

    private void persistLocked() {
        try {
            File file = indexFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("知识库索引目录创建失败:{}", parent.getAbsolutePath());
                return;
            }
            store.save(file);
        } catch (Exception e) {
            // 尽力而为:内存态正确,文件暂旧待下次变更/重建再落
            log.warn("知识库向量索引文件保存失败(内存态不受影响):{}", e.getMessage());
        }
    }

    private File indexFile() {
        return new File(props.getKb().getIndexPath());
    }
}
