package com.own.erp.ai.service;

import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.mapper.AiSuggestionMapper;
import com.own.erp.ai.request.query.AiSuggestionQuery;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * @Description : AiSuggestionService 单测(AIR:mock Mapper 与 CurrentUserApi,不依赖数据库):
 *     读侧映射透传(生成骨架)+ 人工确认闭环真值链路(确认人=当前登录用户,cas 脱靶即拒)
 *     + AI 产出 save 必填校验与 id 回填(与 AiSuggestionStateMachineTest 守卫四类互补)
 */
class AiSuggestionServiceTest {

    private static final Long USER_ID = 99L;

    private AiSuggestionMapper aiSuggestionMapper;
    private CurrentUserApi currentUserApi;
    private AiSuggestionService aiSuggestionService;

    @BeforeEach
    void setUp() {
        aiSuggestionMapper = mock(AiSuggestionMapper.class);
        currentUserApi = mock(CurrentUserApi.class);
        aiSuggestionService = new AiSuggestionService(aiSuggestionMapper, currentUserApi);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        AiSuggestion aiSuggestion = new AiSuggestion();
        aiSuggestion.setId(1L);
        when(aiSuggestionMapper.selectById(1L)).thenReturn(aiSuggestion);
        assertEquals(1L, aiSuggestionService.getById(1L).id());
        assertNull(aiSuggestionService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        AiSuggestion aiSuggestion = new AiSuggestion();
        aiSuggestion.setId(2L);
        Page<AiSuggestion> page = new Page<>(1, 10);
        page.setRecords(List.of(aiSuggestion));
        doReturn(page).when(aiSuggestionMapper).selectPage(any(), any());
        assertEquals(2L, aiSuggestionService.page(new AiSuggestionQuery()).getRecords().get(0).id());
    }

    @Test
    void adoptPassesCurrentUserIdAsConfirmer() {
        when(currentUserApi.currentUserId()).thenReturn(USER_ID);
        when(aiSuggestionMapper.casAdopt(1L, USER_ID)).thenReturn(1);

        aiSuggestionService.adopt(1L);

        verify(aiSuggestionMapper).casAdopt(1L, USER_ID);
    }

    @Test
    void adoptThrowsWhenCasMisses() {
        when(currentUserApi.currentUserId()).thenReturn(USER_ID);
        when(aiSuggestionMapper.casAdopt(1L, USER_ID)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> aiSuggestionService.adopt(1L));
        assertTrue(ex.getMessage().contains("采纳失败"));
    }

    @Test
    void ignoreThrowsWhenCasMisses() {
        when(currentUserApi.currentUserId()).thenReturn(USER_ID);
        when(aiSuggestionMapper.casIgnore(1L, USER_ID)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> aiSuggestionService.ignore(1L));
        assertTrue(ex.getMessage().contains("忽略失败"));
    }

    @Test
    void saveRejectsBlankTypeOrSummary() {
        assertThrows(BusinessException.class, () -> aiSuggestionService.save(
                AiSuggestion.builder().summary("摘要齐但类型缺").build()));
        assertThrows(BusinessException.class, () -> aiSuggestionService.save(
                AiSuggestion.builder().suggestionType("REPLENISH").summary("  ").build()));
        verify(aiSuggestionMapper, never()).insert(any(AiSuggestion.class));
    }

    @Test
    void saveInsertsAndReturnsGeneratedId() {
        doAnswer(inv -> {
            ((AiSuggestion) inv.getArgument(0)).setId(5L);
            return 1;
        }).when(aiSuggestionMapper).insert(any(AiSuggestion.class));

        Long id = aiSuggestionService.save(AiSuggestion.builder()
                .suggestionType("REPLENISH")
                .summary("SKU-1 建议补货 50 件")
                .build());

        assertEquals(5L, id);
    }

    @Test
    void findPendingRefIdsReturnsPendingRefIdsOnly() {
        when(aiSuggestionMapper.selectList(any())).thenReturn(List.of(
                AiSuggestion.builder().refId(11L).build(),
                AiSuggestion.builder().refId(null).build()));

        Set<Long> ids = aiSuggestionService.findPendingRefIds("ANOMALY", "SHOP_ORDER");

        assertEquals(Set.of(11L), ids);
    }

    @Test
    void findPendingSkuIdsReturnsPendingSkuIdsOnly() {
        when(aiSuggestionMapper.selectList(any())).thenReturn(List.of(
                AiSuggestion.builder().skuId(21L).build(),
                AiSuggestion.builder().skuId(22L).build()));

        assertEquals(Set.of(21L, 22L), aiSuggestionService.findPendingSkuIds("REPLENISH"));
    }
}
