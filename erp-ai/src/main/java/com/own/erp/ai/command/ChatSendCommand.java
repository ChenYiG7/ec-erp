package com.own.erp.ai.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 发送对话消息入参(docs/07 §1:写侧 command + @Valid 落点,record+@Builder)
 */
@Builder
public record ChatSendCommand(

        /** 用户问题(即落库 USER 行 content) */
        @NotBlank(message = "消息内容不能为空")
        String message
) {

    /** 手写 toString 脱敏(对话内容含业务敏感上下文,防整体入日志;docs/07 §1 ④) */
    @Override
    public String toString() {
        return "ChatSendCommand(message=***)";
    }
}
