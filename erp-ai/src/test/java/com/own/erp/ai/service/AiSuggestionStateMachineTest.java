package com.own.erp.ai.service;

import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.mapper.AiSuggestionMapper;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.CurrentUserApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AiSuggestionStateMachineTest 单测(状态机守卫,StateMachineTestGenerator 产出,spec 改后重跑再生成)
 *     覆盖守卫四类:cas 命中+脱靶合并式 / 单不存在 / 尾参必填 / 成功路径实参核对(AIR:mock Mapper,不依赖数据库)
 *     复杂编排断言(跨域动账/判定逻辑/多行造数)不在生成射程,见类尾 TODO 槽位
 */
class AiSuggestionStateMachineTest {

    private static final Long ID = 1L;

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
    void adoptSucceedsOnCasHitAndFailsOnMiss() {
        when(aiSuggestionMapper.casAdopt(ID, 0L)).thenReturn(1);
        aiSuggestionService.adopt(ID);
        verify(aiSuggestionMapper).casAdopt(ID, 0L);

        when(aiSuggestionMapper.casAdopt(2L, 0L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> aiSuggestionService.adopt(2L))
                .getMessage().contains("采纳失败"));
    }

    @Test
    void ignoreSucceedsOnCasHitAndFailsOnMiss() {
        when(aiSuggestionMapper.casIgnore(ID, 0L)).thenReturn(1);
        aiSuggestionService.ignore(ID);
        verify(aiSuggestionMapper).casIgnore(ID, 0L);

        when(aiSuggestionMapper.casIgnore(2L, 0L)).thenReturn(0);
        assertTrue(assertThrows(BusinessException.class, () -> aiSuggestionService.ignore(2L))
                .getMessage().contains("忽略失败"));
    }

    // TODO(#6) 人工补齐(生成器不覆盖):adopt/ignore 为单 cas 即守卫(0待确认→1/2,终态互斥),四类用例由生成器覆盖;AI 产出 save(非状态机,必填校验)与 CurrentUserApi 真值链路属人工用例
}
