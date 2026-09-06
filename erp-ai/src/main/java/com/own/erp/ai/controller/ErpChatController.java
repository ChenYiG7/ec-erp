package com.own.erp.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.ai.chat.ErpChatService;
import com.own.erp.ai.command.ChatSendCommand;
import com.own.erp.ai.command.SessionCreateCommand;
import com.own.erp.ai.request.query.AiChatSessionQuery;
import com.own.erp.ai.response.AiChatMessageResponse;
import com.own.erp.ai.response.AiChatSessionResponse;
import com.own.erp.ai.service.AiChatMessageService;
import com.own.erp.ai.service.AiChatSessionService;
import com.own.erp.common.api.Result;
import com.own.erp.contract.CurrentUserApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Lazy;
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
 * @Date : 2026/9/6
 * @Description : AI 助手对话入口(个人助手口径,登录即可,#6):会话管理 + 同步/流式(SSE)双通道对话。
 *     写侧只两动作:新建会话、发消息;消息持久化在 ErpChatService(chat 链路内),无直接写接口。
 *     历史读侧经 AiChatSessionService.getOwned 归属校验(仅本人可见,越权统一"会话不存在");
 *     契约接口注入一律 @Lazy 断构造环(docs/07 §2.2)
 */
@Tag(name = "AI助手", description = "多轮对话:会话管理 + 同步/SSE 流式问答;登录即可,数据仅本人可见")
@RestController
@RequestMapping("/api/ai/chat")
public class ErpChatController {

    private final ErpChatService erpChatService;
    private final AiChatSessionService aiChatSessionService;
    private final AiChatMessageService aiChatMessageService;
    private final CurrentUserApi currentUserApi;

    public ErpChatController(ErpChatService erpChatService,
                             AiChatSessionService aiChatSessionService,
                             AiChatMessageService aiChatMessageService,
                             @Lazy CurrentUserApi currentUserApi) {
        this.erpChatService = erpChatService;
        this.aiChatSessionService = aiChatSessionService;
        this.aiChatMessageService = aiChatMessageService;
        this.currentUserApi = currentUserApi;
    }

    @Operation(summary = "新建会话", description = "标题可空,默认'新会话',首条消息后自动回填为消息摘要")
    @PostMapping("/sessions")
    public Result<Long> createSession(@RequestBody(required = false) @Valid SessionCreateCommand command) {
        return Result.ok(aiChatSessionService.create(
                currentUserApi.currentUserId(), command == null ? null : command.title()));
    }

    @Operation(summary = "我的会话分页", description = "按最近更新倒序;仅本人会话(归属服务端强制)")
    @GetMapping("/sessions")
    public Result<Page<AiChatSessionResponse>> sessions(AiChatSessionQuery query) {
        return Result.ok(aiChatSessionService.pageMine(currentUserApi.currentUserId(), query));
    }

    @Operation(summary = "会话历史消息", description = "时间正序;仅本人会话可查,越权/不存在统一报'会话不存在'")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AiChatMessageResponse>> messages(@PathVariable("id") Long sessionId) {
        aiChatSessionService.getOwned(sessionId, currentUserApi.currentUserId());
        return Result.ok(aiChatMessageService.listBySessionId(sessionId));
    }

    @Operation(summary = "流式对话(SSE)", description = "text/event-stream,逐段返回 AI 回复;消息内容取 body.message")
    @PostMapping(value = "/sessions/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@PathVariable("id") Long sessionId,
                             @Valid @RequestBody ChatSendCommand command) {
        return erpChatService.chatStream(sessionId, command.message());
    }

    @Operation(summary = "同步对话", description = "一次性返回完整回复(带工具查询,耗时高于流式首包)")
    @PostMapping("/sessions/{id}/chat-sync")
    public Result<String> chatSync(@PathVariable("id") Long sessionId,
                                   @Valid @RequestBody ChatSendCommand command) {
        return Result.ok(erpChatService.chat(sessionId, command.message()));
    }
}
