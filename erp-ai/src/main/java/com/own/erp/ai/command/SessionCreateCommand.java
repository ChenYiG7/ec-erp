package com.own.erp.ai.command;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 新建会话入参(docs/07 §1:写侧 command,record+@Builder);
 *         title 可空——空白落默认标题,首条消息后由 AiChatSessionService.renameIfDefault 回填摘要
 */
@Builder
public record SessionCreateCommand(

        /** 会话标题(可空) */
        String title
) {
}
