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
 * @Description : AI会话(多轮对话分组,个人助手口径仅本人可见)(ai_chat_session)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_chat_session")
public class AiChatSession {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID(sys_user.id,会话归属校验依据) */
    private Long userId;

    /** 会话标题(首条提问截断) */
    private String title;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
