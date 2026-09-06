package com.own.erp.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.ai.entity.AiSuggestion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI建议表 Mapper(com.own.erp.ai.mapper):通用 CRUD 走 BaseMapper,
 *         确认状态推进走 cas 条件更新即守卫(同 AftersaleOrderMapper 先例,docs/07 §6.3)
 */
public interface AiSuggestionMapper extends BaseMapper<AiSuggestion> {

    /**
     * 采纳:0待确认 → 1已采纳,同 UPDATE 回填确认人/确认时间;
     * 并发双确认仅一个命中;状态字面量与 AiConsts(STATUS_PENDING/STATUS_ADOPTED)保持同步
     */
    @Update("UPDATE ai_suggestion SET status = 1, confirmed_by = #{confirmedBy}, confirmed_at = NOW() "
            + "WHERE id = #{id} AND status = 0")
    int casAdopt(@Param("id") Long id, @Param("confirmedBy") Long confirmedBy);

    /**
     * 忽略:0待确认 → 2已忽略,同 UPDATE 回填确认人/确认时间;
     * 终态互斥(1/2 互不可达,前置态唯一 0);状态字面量与 AiConsts 保持同步
     */
    @Update("UPDATE ai_suggestion SET status = 2, confirmed_by = #{confirmedBy}, confirmed_at = NOW() "
            + "WHERE id = #{id} AND status = 0")
    int casIgnore(@Param("id") Long id, @Param("confirmedBy") Long confirmedBy);
}
