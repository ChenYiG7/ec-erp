package com.own.erp.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.entity.AiChatMessage;
import com.own.erp.ai.mapper.AiChatMessageMapper;
import com.own.erp.ai.request.query.AiChatMessageQuery;
import com.own.erp.ai.response.AiChatMessageResponse;
import com.own.erp.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AiChatMessageService 单测(AIR:mock Mapper,不依赖数据库):
 *     读侧映射透传(生成骨架)+ append 落库唯一入口(role 封闭词表/content 必填/toolName·tokens 可空)
 *     + 会话历史时间正序读
 */
class AiChatMessageServiceTest {

    private AiChatMessageMapper aiChatMessageMapper;
    private AiChatMessageService aiChatMessageService;

    @BeforeEach
    void setUp() {
        aiChatMessageMapper = mock(AiChatMessageMapper.class);
        aiChatMessageService = new AiChatMessageService(aiChatMessageMapper);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setId(1L);
        when(aiChatMessageMapper.selectById(1L)).thenReturn(aiChatMessage);
        assertEquals(1L, aiChatMessageService.getById(1L).id());
        assertNull(aiChatMessageService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        AiChatMessage aiChatMessage = new AiChatMessage();
        aiChatMessage.setId(2L);
        Page<AiChatMessage> page = new Page<>(1, 10);
        page.setRecords(List.of(aiChatMessage));
        doReturn(page).when(aiChatMessageMapper).selectPage(any(), any());
        assertEquals(2L, aiChatMessageService.page(new AiChatMessageQuery()).getRecords().get(0).id());
    }

    @Test
    void appendRejectsInvalidRoleOrBlankContent() {
        assertThrows(BusinessException.class,
                () -> aiChatMessageService.append(1L, "SYSTEM", "词表外角色", null, null, null));
        assertThrows(BusinessException.class,
                () -> aiChatMessageService.append(1L, "USER", "  ", null, null, null));
        assertThrows(BusinessException.class,
                () -> aiChatMessageService.append(null, "USER", "会话缺失", null, null, null));
        verify(aiChatMessageMapper, never()).insert(any(AiChatMessage.class));
    }

    @Test
    void appendInsertsWithNullableToolFieldsAndReturnsId() {
        doAnswer(inv -> {
            ((AiChatMessage) inv.getArgument(0)).setId(9L);
            return 1;
        }).when(aiChatMessageMapper).insert(any(AiChatMessage.class));

        Long id = aiChatMessageService.append(1L, "AI", "回答文本", null, 100, 20);

        assertEquals(9L, id);
        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(aiChatMessageMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getSessionId());
        assertEquals("AI", captor.getValue().getRole());
        assertEquals("回答文本", captor.getValue().getContent());
        assertNull(captor.getValue().getToolName());
        assertEquals(100, captor.getValue().getPromptTokens());
        assertEquals(20, captor.getValue().getCompletionTokens());
    }

    @Test
    void listBySessionIdMapsChronologically() {
        AiChatMessage first = new AiChatMessage();
        first.setId(1L);
        first.setSessionId(5L);
        first.setRole("USER");
        first.setContent("问");
        AiChatMessage second = new AiChatMessage();
        second.setId(2L);
        second.setSessionId(5L);
        second.setRole("AI");
        second.setContent("答");
        when(aiChatMessageMapper.selectList(any())).thenReturn(List.of(first, second));

        List<AiChatMessageResponse> result = aiChatMessageService.listBySessionId(5L);

        assertEquals(2, result.size());
        assertEquals("USER", result.get(0).role());
        assertEquals("答", result.get(1).content());
    }
}
