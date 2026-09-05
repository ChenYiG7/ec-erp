package com.own.erp.ai.chat;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : AI 入口占位类 —— 按约定"AI 代码先不写",仅保留装配骨架,由人工实现(TODO.md #6)。
 *
 *     实现指引(参考启航 qihang-erp 的 AI 模块):
 *     1. 注入 ChatClient.Builder(Spring AI 2.0 自动装配,模型由 spring.ai.openai.* 配置);
 *     2. 自然语言查询:ChatClient.prompt().system("你是ERP助手,只能调用工具查询,禁止编造数据")
 *            .tools(erpTools)   // tools 包下注册的只读 @Tool
 *            .user(question).call().content();
 *     3. 流式:改用 .stream().content() 返回 Flux,Controller 用 SSE(text/event-stream)输出;
 *     4. 权限:AI 只授只读工具,写操作(改库存/改价)必须人工确认后走正常接口。
 */
@Service
public class ErpChatService {

    private final ChatModel chatModel;

    public ErpChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String chat(String question) {
        // TODO(#6 AI): 按「实现指引」补全,先封死防止误用
        throw new UnsupportedOperationException("TODO: AI 对话由人工实现,见 TODO.md #6 与本类注释");
    }
}
