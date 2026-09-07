package com.own.erp.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiChatSession;
import com.own.erp.ai.mapper.AiChatSessionMapper;
import com.own.erp.ai.request.query.AiChatSessionQuery;
import com.own.erp.ai.response.AiChatSessionResponse;
import com.own.erp.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AiChatSessionService 单测(AIR:mock Mapper,不依赖数据库):
 *     我的会话归属服务端强制(防越权入参)、标题默认与截断、归属校验统一口径(不泄露存在性)、
 *     标题回填仅默认标题生效
 */
class AiChatSessionServiceTest {

    private static final Long USER_ID = 99L;

    private AiChatSessionMapper aiChatSessionMapper;
    private AiChatSessionService aiChatSessionService;

    @BeforeEach
    void setUp() {
        aiChatSessionMapper = mock(AiChatSessionMapper.class);
        aiChatSessionService = new AiChatSessionService(aiChatSessionMapper);
    }

    @Test
    void pageMineForcesOwnershipToCurrentUser() {
        doReturn(new Page<AiChatSession>(1, 10)).when(aiChatSessionMapper).selectPage(any(), any());

        AiChatSessionQuery query = new AiChatSessionQuery();
        query.setUserId(1L);
        aiChatSessionService.pageMine(USER_ID, query, AiConsts.SESSION_SOURCE_CHAT);

        assertEquals(USER_ID, query.getUserId());
    }

    @Test
    void pageMineMapsRecordsToResponse() {
        Page<AiChatSession> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(AiChatSession.builder().id(2L).userId(USER_ID).title("T").build()));
        doReturn(page).when(aiChatSessionMapper).selectPage(any(), any());

        Page<AiChatSessionResponse> result = aiChatSessionService.pageMine(USER_ID, new AiChatSessionQuery(), AiConsts.SESSION_SOURCE_CHAT);

        assertEquals(1, result.getRecords().size());
        assertEquals(2L, result.getRecords().get(0).id());
    }

    @Test
    void createDefaultsBlankTitleAndTruncatesLong() {
        doAnswer(inv -> {
            ((AiChatSession) inv.getArgument(0)).setId(7L);
            return 1;
        }).when(aiChatSessionMapper).insert(any(AiChatSession.class));

        assertEquals(7L, aiChatSessionService.create(USER_ID, "  "));
        verify(aiChatSessionMapper).insert(
                ArgumentMatchers.<AiChatSession>argThat(s -> AiConsts.DEFAULT_SESSION_TITLE.equals(s.getTitle())));

        aiChatSessionService.create(USER_ID, "A".repeat(30));
        ArgumentCaptor<AiChatSession> captor = ArgumentCaptor.forClass(AiChatSession.class);
        verify(aiChatSessionMapper, times(2)).insert(captor.capture());
        assertEquals(AiConsts.TITLE_MAX_LEN, captor.getValue().getTitle().length());
    }

    @Test
    void getOwnedRejectsMissingOrForeignSession() {
        when(aiChatSessionMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> aiChatSessionService.getOwned(1L, USER_ID, AiConsts.SESSION_SOURCE_CHAT));

        when(aiChatSessionMapper.selectById(2L)).thenReturn(AiChatSession.builder()
                .id(2L).userId(1L).title("t").source(AiConsts.SESSION_SOURCE_CHAT).build());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> aiChatSessionService.getOwned(2L, USER_ID, AiConsts.SESSION_SOURCE_CHAT));
        assertEquals("会话不存在", ex.getMessage());

        assertEquals(2L, aiChatSessionService.getOwned(2L, 1L, AiConsts.SESSION_SOURCE_CHAT).getId());
    }

    @Test
    void getOwnedRejectsCrossSourceSession() {
        // 跨源强约束:AGENT 会话在 chat 域同报"会话不存在"(不泄露跨域存在性),agent 域正常通过
        when(aiChatSessionMapper.selectById(3L)).thenReturn(AiChatSession.builder()
                .id(3L).userId(USER_ID).title("t").source(AiConsts.SESSION_SOURCE_AGENT).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aiChatSessionService.getOwned(3L, USER_ID, AiConsts.SESSION_SOURCE_CHAT));
        assertEquals("会话不存在", ex.getMessage());

        assertEquals(3L, aiChatSessionService.getOwned(3L, USER_ID, AiConsts.SESSION_SOURCE_AGENT).getId());
    }

    @Test
    void renameIfDefaultOnlyTouchesDefaultTitle() {
        when(aiChatSessionMapper.selectById(1L))
                .thenReturn(AiChatSession.builder().id(1L).userId(USER_ID).title(AiConsts.DEFAULT_SESSION_TITLE).build());
        when(aiChatSessionMapper.selectById(2L))
                .thenReturn(AiChatSession.builder().id(2L).userId(USER_ID).title("已命名").build());

        aiChatSessionService.renameIfDefault(1L, "A".repeat(30));
        ArgumentCaptor<AiChatSession> captor = ArgumentCaptor.forClass(AiChatSession.class);
        verify(aiChatSessionMapper).updateById(captor.capture());
        assertEquals(AiConsts.TITLE_MAX_LEN, captor.getValue().getTitle().length());

        aiChatSessionService.renameIfDefault(2L, "另一个名字");
        verify(aiChatSessionMapper, times(1)).updateById(any(AiChatSession.class));
    }
}
