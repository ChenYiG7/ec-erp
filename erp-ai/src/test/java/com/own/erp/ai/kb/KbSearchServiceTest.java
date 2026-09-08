package com.own.erp.ai.kb;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.graph.RuntimePropsStub;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : KbSearchService 单测(#6 RAG V1,AIR):top-k=0 关闭(检索不进行)/KB 空返回空串/
 *     命中拼装格式(头 + 编号片段)/空问题短路;AiRuntimeProperties 走 RuntimePropsStub(yml 默认)
 */
class KbSearchServiceTest {

    private final KbVectorIndex vectorIndex = mock(KbVectorIndex.class);
    private final ErpAiProperties props = new ErpAiProperties();
    private final KbSearchService service = new KbSearchService(vectorIndex, RuntimePropsStub.of(props));

    @Test
    void zeroTopKDisablesRetrieval() {
        props.getKb().setRetrievalTopK(0);
        assertEquals("", service.buildContext("退货怎么处理?"));
        verifyNoInteractions(vectorIndex);
    }

    @Test
    void blankQuestionShortCircuits() {
        assertEquals("", service.buildContext("  "));
        verifyNoInteractions(vectorIndex);
    }

    @Test
    void emptyIndexReturnsEmptyContext() {
        when(vectorIndex.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());
        assertEquals("", service.buildContext("退货怎么处理?"));
    }

    @Test
    void hitsAreAssembledIntoContextBlock() {
        when(vectorIndex.search(eq("退货怎么处理?"), eq(4), eq(0.5))).thenReturn(List.of(
                new Document("客户申请退货后,先核对签收时间。"),
                new Document("退货须在签收后 7 天内发起。")));

        String context = service.buildContext("退货怎么处理?");

        assertTrue(context.startsWith("\n\n"));
        assertTrue(context.contains(KbSearchService.CONTEXT_HEADER));
        assertTrue(context.contains("[1] 客户申请退货后,先核对签收时间。"));
        assertTrue(context.contains("[2] 退货须在签收后 7 天内发起。"));
        assertFalse(context.contains("[3]"));
    }

    @Test
    void blankHitTextIsSkippedFromAssembly() {
        when(vectorIndex.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of(
                new Document("   "),
                new Document("唯一有效片段")));
        String context = service.buildContext("问题");
        assertFalse(context.contains("[2]"));
        assertTrue(context.contains("[1] 唯一有效片段"));
    }

    @Test
    void runtimeOverrideWinsOverYmlDefault() {
        when(vectorIndex.search(anyString(), anyInt(), anyDouble())).thenReturn(List.of());
        KbSearchService overridden = new KbSearchService(vectorIndex, RuntimePropsStub.of(props, java.util.Map.of(
                "erp.ai.kb.retrieval-top-k", "8",
                "erp.ai.kb.retrieval-min-score", "0.6")));
        overridden.buildContext("问题");
        verify(vectorIndex).search("问题", 8, 0.6);
    }
}
