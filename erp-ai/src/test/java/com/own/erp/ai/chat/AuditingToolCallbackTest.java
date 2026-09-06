package com.own.erp.ai.chat;

import com.own.erp.ai.service.AiChatMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AuditingToolCallback 单测(#6,AIR:mock delegate,不出网):
 *     invoke 前落 TOOL 行(工具名+入参,失败也留痕)、超长入参截断、两参 call 带 ToolContext 直转、
 *     definition/metadata 纯转发
 */
class AuditingToolCallbackTest {

    private static final Long SESSION_ID = 1L;

    private ToolCallback delegate;
    private AiChatMessageService messageService;
    private AuditingToolCallback callback;
    private ToolDefinition definition;

    @BeforeEach
    void setUp() {
        delegate = mock(ToolCallback.class);
        definition = ToolDefinition.builder()
                .name("queryInventory")
                .description("查库存")
                .inputSchema("{}")
                .build();
        when(delegate.getToolDefinition()).thenReturn(definition);
        messageService = mock(AiChatMessageService.class);
        callback = new AuditingToolCallback(delegate, SESSION_ID, messageService, 500);
    }

    @Test
    void callAuditsToolRowBeforeDelegateInvoke() {
        when(delegate.call("{\"skuId\":1}")).thenReturn("[{\"qty\":5}]");

        assertEquals("[{\"qty\":5}]", callback.call("{\"skuId\":1}"));

        InOrder inOrder = inOrder(messageService, delegate);
        inOrder.verify(messageService).append(SESSION_ID, "TOOL", "{\"skuId\":1}",
                "queryInventory", null, null);
        inOrder.verify(delegate).call("{\"skuId\":1}");
    }

    @Test
    void callTruncatesOversizedInput() {
        AuditingToolCallback strictCallback = new AuditingToolCallback(delegate, SESSION_ID, messageService, 10);
        String oversized = "x".repeat(50);
        when(delegate.call(oversized)).thenReturn("ok");

        strictCallback.call(oversized);

        // auditMaxLength=10:截断为 10 字符 + "..."(StrUtil.maxLength)
        verify(messageService).append(SESSION_ID, "TOOL", "xxxxxxxxxx...",
                "queryInventory", null, null);
    }

    @Test
    void callAuditsEvenWhenDelegateThrows() {
        when(delegate.call(anyString())).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> callback.call("{}"));

        verify(messageService).append(SESSION_ID, "TOOL", "{}", "queryInventory", null, null);
    }

    @Test
    void callWithContextAuditsAndForwards() {
        ToolContext context = new ToolContext(Map.of("k", "v"));
        when(delegate.call("{}", context)).thenReturn("ok");

        assertEquals("ok", callback.call("{}", context));

        verify(messageService).append(eq(SESSION_ID), eq("TOOL"), eq("{}"),
                eq("queryInventory"), isNull(), isNull());
        verify(delegate).call("{}", context);
    }

    @Test
    void forwardsDefinitionAndMetadata() {
        assertSame(definition, callback.getToolDefinition());
        ToolMetadata metadata = mock(ToolMetadata.class);
        when(delegate.getToolMetadata()).thenReturn(metadata);
        assertSame(metadata, callback.getToolMetadata());
        verify(delegate).getToolMetadata();
    }
}
