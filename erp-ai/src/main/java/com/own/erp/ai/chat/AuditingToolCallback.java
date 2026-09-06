package com.own.erp.ai.chat;

import cn.hutool.core.util.StrUtil;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiChatMessageService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 工具调用审计装饰器(#6):包装 ToolCallback,invoke 前把工具名+入参落
 *     ai_chat_message TOOL 中间行(先落库再委托,工具执行失败也留痕,异常原样上抛不吞);
 *     ErpChatService 经 ToolCallbacks.from 把只读工具白名单转回调数组后逐个包装,走
 *     ChatClient.toolCallbacks,Spring AI 内部工具执行循环不变,同步/流式双通道统一生效。
 *     content 记录入参 JSON 截断(erp.ai.tool-audit-max-length),tokens 不适用置 NULL;
 *     持有 sessionId,每请求新建,禁跨请求复用
 */
public class AuditingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final Long sessionId;
    private final AiChatMessageService messageService;
    private final int auditMaxLength;

    public AuditingToolCallback(ToolCallback delegate, Long sessionId,
                                AiChatMessageService messageService, int auditMaxLength) {
        this.delegate = delegate;
        this.sessionId = sessionId;
        this.messageService = messageService;
        this.auditMaxLength = auditMaxLength;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        audit(toolInput);
        return delegate.call(toolInput);
    }

    /** 显式覆写两参版直转 delegate(接口 default 实现会丢 ToolContext);ToolContext 链路审计口径同单参 */
    @Override
    public String call(String toolInput, ToolContext toolContext) {
        audit(toolInput);
        return delegate.call(toolInput, toolContext);
    }

    private void audit(String toolInput) {
        messageService.append(sessionId, AiConsts.ROLE_TOOL,
                StrUtil.maxLength(toolInput, auditMaxLength),
                delegate.getToolDefinition().name(), null, null);
    }
}
