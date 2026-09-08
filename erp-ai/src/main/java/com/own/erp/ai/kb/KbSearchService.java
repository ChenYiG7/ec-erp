package com.own.erp.ai.kb;

import cn.hutool.core.util.StrUtil;
import com.own.erp.ai.config.AiRuntimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 知识库检索服务(#6 AI 客服 RAG V1):chat 提问 → 向量相似检索 top-k →
 *     拼装参考上下文注入 user message(不用 Advisor 魔法,显式可测;USER 审计行仍存原始问题)。
 *     降级语义:KB 无 READY 文档/检索空/命中不足/任何异常 → 返回空串,prompt 原样(零侵入);
 *     RAG 失败绝不阻断 chat(同"无 key 降级不炸"口径)。top-k=0 视为关闭注入(检索不进行)。
 *     V1 只接 chat 双通道,agent(四期)不注入
 */
@Service
@Slf4j
public class KbSearchService {

    /** 上下文注入头(固定格式,不进配置——结构稳定非调参面) */
    static final String CONTEXT_HEADER = """
            【知识库参考资料】以下是知识库中与问题相关的片段,回答时可以参考;\
            若与工具查询到的数据冲突,以工具查询为准;资料与问题无关时忽略。""";

    private final KbVectorIndex vectorIndex;
    private final AiRuntimeProperties runtime;

    public KbSearchService(KbVectorIndex vectorIndex, AiRuntimeProperties runtime) {
        this.vectorIndex = vectorIndex;
        this.runtime = runtime;
    }

    /** 检索并拼装上下文;无命中/关闭/异常返回空串(调用方判空原样透传) */
    public String buildContext(String question) {
        int topK = runtime.kbRetrievalTopK();
        if (topK <= 0 || StrUtil.isBlank(question)) {
            return "";
        }
        List<Document> hits = vectorIndex.search(question, topK, runtime.kbRetrievalMinScore());
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n").append(CONTEXT_HEADER);
        int i = 1;
        for (Document hit : hits) {
            String text = StrUtil.trim(hit.getText());
            if (StrUtil.isBlank(text)) {
                continue;
            }
            sb.append("\n[").append(i++).append("] ").append(text);
        }
        // 全部命中块为空文本(理论不发生)时视同无命中
        return i == 1 ? "" : sb.toString();
    }
}
