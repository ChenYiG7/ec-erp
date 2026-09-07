package com.own.erp.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.agent.AgentRole;
import com.own.erp.ai.agent.AgentService;
import com.own.erp.ai.command.AgentChatCommand;
import com.own.erp.ai.command.SessionCreateCommand;
import com.own.erp.ai.request.query.AiChatSessionQuery;
import com.own.erp.ai.response.AiChatMessageResponse;
import com.own.erp.ai.response.AiChatSessionResponse;
import com.own.erp.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AI Agent 端点(四期 agent/ V1.5,AgentScope ReActAgent 会话式多轮):
 *     会话域与 chat 同构(新建/列表/历史,source=AGENT 隔离,归属服务端强制,越权统一"会话不存在");
 *     对话双通道:SSE 流式(逐 TextBlock delta)+ chat-sync 同步聚合;登录即可(同 chat 口径)。
 *     流式帧 = 裸文本 data: 块(同 chatStream 形态,前端解析零改动)
 */
@Tag(name = "AI Agent", description = "AgentScope ReActAgent:客服(全量只读工具)/运营(库存商品盘面);会话与审计复用 ai_chat_* 表;写操作提示人工页面处理")
@RestController
@RequestMapping("/api/ai/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @Operation(summary = "新建 Agent 会话", description = "source=AGENT 隔离;标题可空,首条消息后自动回填摘要")
    @PostMapping("/{role}/sessions")
    public Result<Long> createSession(@PathVariable("role") String role,
                                      @RequestBody(required = false) @Valid SessionCreateCommand command) {
        AgentRole.fromPath(role);
        return Result.ok(agentService.createSession(command == null ? null : command.title()));
    }

    @Operation(summary = "我的 Agent 会话分页", description = "仅 source=AGENT 会话(与智能对话列表隔离);按最近更新倒序")
    @GetMapping("/{role}/sessions")
    public Result<Page<AiChatSessionResponse>> sessions(@PathVariable("role") String role,
                                                        AiChatSessionQuery query) {
        AgentRole.fromPath(role);
        return Result.ok(agentService.pageSessions(query));
    }

    @Operation(summary = "会话历史消息", description = "时间正序;仅本人会话可查,越权/不存在统一报'会话不存在'")
    @GetMapping("/{role}/sessions/{sessionId}/messages")
    public Result<List<AiChatMessageResponse>> messages(@PathVariable("role") String role,
                                                        @PathVariable("sessionId") Long sessionId) {
        AgentRole.fromPath(role);
        return Result.ok(agentService.listMessages(sessionId));
    }

    @Operation(summary = "Agent 对话(SSE 流式)", description = "历史重放多轮上下文;ReAct 循环自动调只读工具,逐 TextBlock delta 推送,帧形态同智能对话")
    @PostMapping(value = "/{role}/sessions/{sessionId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@PathVariable("role") String role,
                             @PathVariable("sessionId") Long sessionId,
                             @Valid @RequestBody AgentChatCommand command) {
        AgentRole.fromPath(role);
        return agentService.chatStream(AgentRole.fromPath(role), sessionId, command.message());
    }

    @Operation(summary = "Agent 对话(同步)", description = "聚合全部 delta 一次性返回;SSE 不可用时的降级通道")
    @PostMapping("/{role}/sessions/{sessionId}/chat-sync")
    public Result<String> chatSync(@PathVariable("role") String role,
                                   @PathVariable("sessionId") Long sessionId,
                                   @Valid @RequestBody AgentChatCommand command) {
        AgentRole.fromPath(role);
        return Result.ok(agentService.chatSync(AgentRole.fromPath(role), sessionId, command.message()));
    }
}
