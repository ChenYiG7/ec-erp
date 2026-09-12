package com.own.erp.system.controller;

import com.own.erp.system.auth.AuthContext;
import com.own.erp.system.service.NotificationSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 站内通知 SSE 订阅端点(浏览器实时推送,SSE 计划书):登录即连,Bearer 鉴权
 *     走 /api/** 既有 JWT 收口;前端 EventSource 带不了 Bearer,故用 POST + fetch 手解
 *     (erp-web utils/sse.ts 通道,与 chat/agent 同款)。开关关闭回 404 语义,前端降级 60s 轮询。
 */
@Tag(name = "站内通知", description = "站内通知(系统告警,只读+本人已读状态)")
@RestController
@RequestMapping("/api/system/notifications")
@RequiredArgsConstructor
public class NotificationSseController {

    private final NotificationSseService notificationSseService;

    @Operation(summary = "订阅通知实时推送(SSE)", description = "text/event-stream 长连接:通知帧 JSON"
            + "{notifyType,title,content,bizType,bizId} + 30s 心跳帧{type:HEARTBEAT};开关关闭时 404(前端降级轮询)")
    @PostMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> subscribe() {
        if (!notificationSseService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SSE 通知通道未启用");
        }
        return notificationSseService.subscribe(AuthContext.current().userId());
    }
}
