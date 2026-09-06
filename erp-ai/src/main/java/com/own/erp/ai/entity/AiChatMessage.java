package com.own.erp.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI会话消息(对话落库可审计)(ai_chat_message)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_message")
public class AiChatMessage {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID(ai_chat_session.id) */
    private Long sessionId;

    /** 消息角色:USER用户/AI助手/TOOL工具调用 */
    private String role;

    /** 消息内容 */
    private String content;

    /** 工具名(role=TOOL时记录,审计用) */
    private String toolName;

    /** 输入token用量(模型未回传则NULL) */
    private Integer promptTokens;

    /** 输出token用量(模型未回传则NULL) */
    private Integer completionTokens;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
