package com.own.erp.ai.response;

import com.own.erp.ai.entity.AiChatMessage;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI会话消息对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进组件即编译期封死
 */
@Builder
public record AiChatMessageResponse(

        /** 主键 */
        Long id,

        /** 会话ID(ai_chat_session.id) */
        Long sessionId,

        /** 消息角色:USER用户/AI助手/TOOL工具调用 */
        String role,

        /** 消息内容 */
        String content,

        /** 工具名(role=TOOL时记录,审计用) */
        String toolName,

        /** 输入token用量(模型未回传则NULL) */
        Integer promptTokens,

        /** 输出token用量(模型未回传则NULL) */
        Integer completionTokens,

        /** 创建时间 */
        LocalDateTime createdAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static AiChatMessageResponse from(AiChatMessage entity) {
        return AiChatMessageResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(entity.getRole())
                .content(entity.getContent())
                .toolName(entity.getToolName())
                .promptTokens(entity.getPromptTokens())
                .completionTokens(entity.getCompletionTokens())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
