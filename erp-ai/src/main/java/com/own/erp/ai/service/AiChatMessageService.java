package com.own.erp.ai.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiChatMessage;
import com.own.erp.ai.mapper.AiChatMessageMapper;
import com.own.erp.ai.request.query.AiChatMessageQuery;
import com.own.erp.ai.response.AiChatMessageResponse;
import com.own.erp.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI会话消息服务:ai_chat_message 域整域收口,Controller 不直连 Mapper(docs/07 §2.1)。
 *     写侧唯一入口 append(会话持久化,USER/AI 行;TOOL 中间行为词表预留,当前 Spring AI 工具执行循环
 *     不透出中间消息,tool_name/tokens 尽力而为取不到置 NULL,docs/03 §7);
 *     读侧 listBySessionId 时间正序(归属校验由调用方经 AiChatSessionService.getOwned 收口)
 */
@Service
public class AiChatMessageService {

    /** role 封闭词表(AiConsts),防脏数据入审计表 */
    private static final Set<String> ALLOWED_ROLES = Set.of(
            AiConsts.ROLE_USER, AiConsts.ROLE_AI, AiConsts.ROLE_TOOL);

    private final AiChatMessageMapper aiChatMessageMapper;

    public AiChatMessageService(AiChatMessageMapper aiChatMessageMapper) {
        this.aiChatMessageMapper = aiChatMessageMapper;
    }

    /** 分页查询(默认按 id 倒序;过滤条件在 AiChatMessageQuery 加字段后在此补 Wrapper 条件) */
    public Page<AiChatMessageResponse> page(AiChatMessageQuery query) {
        Page<AiChatMessage> result = aiChatMessageMapper.selectPage(new Page<>(query.getPageNo(), query.pageSize()),
                new LambdaQueryWrapper<AiChatMessage>().orderByDesc(AiChatMessage::getId));
        Page<AiChatMessageResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        responsePage.setRecords(result.getRecords().stream().map(AiChatMessageResponse::from).toList());
        return responsePage;
    }

    /** 详情,出参 Response;不存在返回 null */
    public AiChatMessageResponse getById(Long id) {
        AiChatMessage aiChatMessage = aiChatMessageMapper.selectById(id);
        return aiChatMessage == null ? null : AiChatMessageResponse.from(aiChatMessage);
    }

    /**
     * 追加消息(会话持久化唯一入口,禁旁路 insert):role 走封闭词表,content 必填;
     * toolName/promptTokens/completionTokens 尽力而为可空(流式链路 usage 取不到置 NULL)
     */
    public Long append(Long sessionId, String role, String content, String toolName,
                       Integer promptTokens, Integer completionTokens) {
        if (sessionId == null || !ALLOWED_ROLES.contains(role) || StrUtil.isBlank(content)) {
            throw new BusinessException("会话消息落库参数不合法(sessionId/role/content)");
        }
        AiChatMessage message = AiChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .toolName(toolName)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .build();
        aiChatMessageMapper.insert(message);
        return message.getId();
    }

    /** 会话历史(时间正序,同会话内按 id 兜底稳定排序);归属校验由调用方经 getOwned 完成 */
    public List<AiChatMessageResponse> listBySessionId(Long sessionId) {
        return aiChatMessageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getCreatedAt)
                        .orderByAsc(AiChatMessage::getId))
                .stream().map(AiChatMessageResponse::from).toList();
    }
}
