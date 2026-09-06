package com.own.erp.ai.chat;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.service.AiChatMessageService;
import com.own.erp.ai.service.AiChatSessionService;
import com.own.erp.ai.tools.AftersaleTools;
import com.own.erp.ai.tools.GoodsTools;
import com.own.erp.ai.tools.InventoryTools;
import com.own.erp.ai.tools.OrderTools;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : ErpChatService 单测(#6,AIR:mock ChatClient 链路,全程不出网):
 *     无 API_KEY 友好拦截(启动不炸)、归属校验前置、USER/AI 双行落库(同步带 usage,流式聚合落库置 NULL);
 *     prompt 链路用显式桩(RETURNS_SELF + 终端显式 stub),不走深桩——toolCallbacks 为 varargs,
 *     深桩对数组入参的链路匹配不可靠(Mockito 5 坑,#6 T1 实测)
 */
class ErpChatServiceTest {

    private static final Long USER_ID = 99L;

    private ChatClient.ChatClientRequestSpec spec;
    private AiChatSessionService sessionService;
    private AiChatMessageService messageService;
    private ErpChatService service;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        spec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(spec);
        CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
        when(currentUserApi.currentUserId()).thenReturn(USER_ID);
        sessionService = mock(AiChatSessionService.class);
        messageService = mock(AiChatMessageService.class);
        service = new ErpChatService(builder, new ErpAiProperties(), currentUserApi, sessionService,
                messageService, mock(OrderTools.class), mock(InventoryTools.class),
                mock(GoodsTools.class), mock(AftersaleTools.class));
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
    }

    private void stubCallChain(ChatResponse response) {
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(spec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(response);
    }

    @Test
    void chatRejectsWhenApiKeyMissing() {
        ReflectionTestUtils.setField(service, "apiKey", " ");
        assertThrows(BusinessException.class, () -> service.chat(1L, "你好"));
        verifyNoInteractions(messageService);
    }

    @Test
    void chatRejectsBlankMessage() {
        assertThrows(BusinessException.class, () -> service.chat(1L, "   "));
        verifyNoInteractions(messageService);
    }

    @Test
    void chatRejectsWhenSessionNotOwned() {
        doThrow(new BusinessException("会话不存在")).when(sessionService).getOwned(1L, USER_ID);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.chat(1L, "hi"));
        assertTrue(ex.getMessage().contains("会话不存在"));
        verifyNoInteractions(messageService);
    }

    @Test
    void chatPersistsUserAndAiMessagesWithUsage() {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("库存充足"));
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(response.getMetadata()).thenReturn(metadata);
        Usage usage = mock(Usage.class);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(100);
        when(usage.getCompletionTokens()).thenReturn(20);
        stubCallChain(response);

        String reply = service.chat(1L, "查一下SKU-1的库存");

        assertEquals("库存充足", reply);
        verify(sessionService).getOwned(1L, USER_ID);
        verify(sessionService).renameIfDefault(eq(1L), eq("查一下SKU-1的库存"));
        verify(messageService).append(1L, "USER", "查一下SKU-1的库存", null, null, null);
        verify(messageService).append(1L, "AI", "库存充足", null, 100, 20);
    }

    @Test
    void chatStreamPersistsAggregatedReplyOnComplete() {
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(spec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("库存", "充足"));

        service.chatStream(1L, "hi").blockLast();

        verify(messageService).append(eq(1L), eq("USER"), eq("hi"), isNull(), isNull(), isNull());
        verify(messageService).append(eq(1L), eq("AI"), eq("库存充足"), isNull(), isNull(), isNull());
    }

    @Test
    void chatStreamErrorEmitsFallbackFrameAndSkipsAiRow() {
        // 联调校准(2026-09-07):上游模型异常不得伪装成 401 未登录,转可见错误帧;失败轮不落 AI 行
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(spec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.error(new RuntimeException("401: api key invalid")));

        java.util.List<String> chunks = service.chatStream(1L, "hi").collectList().block();

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("AI 调用失败"));
        assertTrue(chunks.get(0).contains("401: api key invalid"));
        verify(messageService).append(eq(1L), eq("USER"), eq("hi"), isNull(), isNull(), isNull());
        verify(messageService, never()).append(eq(1L), eq("AI"), any(), any(), any(), any());
    }
}
