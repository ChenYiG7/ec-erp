package com.own.erp.ai.agent;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiChatMessageService;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : Spring AI 工具桥(四期 agent/):把 tools/ 只读 @Tool 工具(Spring AI ToolCallback 形态)
 *     适配成 AgentScope 的 AgentTool,供 ReActAgent Toolkit 注册——工具逻辑单一来源仍在 tools/,
 *     Agent 侧零复制。带 sessionId 时工具调用前落 TOOL 审计行(入参 JSON 截断,同 AuditingToolCallback 口径);
 *     工具执行失败不炸 Agent 调用:错误作为工具结果回给模型自行决策
 */
@Slf4j
public class SpringAiAgentToolBridge implements AgentTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolCallback callback;
    /** 会话审计(可空=不落库:仅列工具面等非执行场景) */
    private final Long sessionId;
    private final AiChatMessageService messageService;
    private final int auditMaxLength;

    public SpringAiAgentToolBridge(ToolCallback callback) {
        this(callback, null, null, 0);
    }

    public SpringAiAgentToolBridge(ToolCallback callback, Long sessionId,
                                   AiChatMessageService messageService, int auditMaxLength) {
        this.callback = callback;
        this.sessionId = sessionId;
        this.messageService = messageService;
        this.auditMaxLength = auditMaxLength;
    }

    @Override
    public String getName() {
        return callback.getToolDefinition().name();
    }

    @Override
    public String getDescription() {
        return callback.getToolDefinition().description();
    }

    @Override
    public Map<String, Object> getParameters() {
        try {
            return OBJECT_MAPPER.readValue(callback.getToolDefinition().inputSchema(),
                    new TypeReference<>() {
                    });
        } catch (Exception e) {
            // schema 解析失败按无参工具兜底(参数 schema 只影响模型填写,不影响执行)
            log.warn("工具 {} inputSchema 解析失败,按无参兜底 :{}", getName(), e.getMessage());
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        ToolUseBlock toolUse = param.getToolUseBlock();
        return Mono.fromCallable(() -> {
                    Map<String, Object> input = param.getInput() == null
                            ? toolUse.getInput() : param.getInput();
                    String argsJson = OBJECT_MAPPER.writeValueAsString(input == null ? Map.of() : input);
                    auditToolCall(toolUse.getName(), argsJson);
                    return callback.call(argsJson);
                })
                .map(result -> new ToolResultBlock(toolUse.getId(), toolUse.getName(),
                        TextBlock.builder().text(StrUtil.nullToEmpty(result)).build()))
                .onErrorResume(e -> {
                    // 工具失败作为结果回给模型(ReAct 可自行重试/改答),不阻断整个 Agent 调用
                    log.warn("Agent 工具 {} 执行失败 :{}", toolUse.getName(), e.getMessage());
                    return Mono.just(new ToolResultBlock(toolUse.getId(), toolUse.getName(),
                            TextBlock.builder()
                                    .text(StrUtil.format("工具执行失败:{}", e.getMessage()))
                                    .build()));
                });
    }

    /** TOOL 审计行:工具调用前落库(入参 JSON 截断,同 AuditingToolCallback 口径);写失败只记日志不阻断 */
    private void auditToolCall(String toolName, String argsJson) {
        if (messageService == null) {
            return;
        }
        try {
            messageService.append(sessionId, AiConsts.ROLE_TOOL,
                    StrUtil.maxLength(argsJson, auditMaxLength), toolName, null, null);
        } catch (Exception e) {
            log.warn("Agent 工具审计行落库失败 tool={} :{}", toolName, e.getMessage());
        }
    }
}
