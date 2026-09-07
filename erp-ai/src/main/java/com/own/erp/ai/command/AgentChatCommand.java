package com.own.erp.ai.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : Agent 对话入参(四期 agent/,docs/07 §1:写侧 command + @Valid 落点,record+@Builder);
 *     V1 单轮无状态,消息不落库(多轮记忆/审计随四期推进另拍板)
 */
@Builder
public record AgentChatCommand(

        /** 用户问题 */
        @NotBlank(message = "消息内容不能为空")
        String message
) {

    /** 手写 toString 脱敏(同 ChatSendCommand 口径,docs/07 §1 ④) */
    @Override
    public String toString() {
        return "AgentChatCommand(message=***)";
    }
}
