package com.own.erp.system.service;

import com.own.erp.system.event.NotifyPushedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : SSE 通知推送服务(浏览器实时通道,SSE 计划书):pushAllUsers 出口的第四个消费者——
 *     <p>相位拍板:与 Webhook/Mail 同口径 {@code @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)},
 *     事务提交后才推帧;推送失败只影响该条连接(broadcast 内注销断连),绝不回滚站内通知、不上抛。
 *     <p>语义拍板:pushAllUsers = 全量扇出,事件无收件人列表,广播不做用户过滤(计划书明确"别造");
 *     前端收帧后自行 refresh 未读数,不冗余带计数。
 *     <p>开关:erp.notify.sse.enabled 默认 true;关闭时订阅端点 404、监听器不推(前端降级轮询,
 *     语义 = 整通道降级而非报错);Webhook/Mail 两监听器互不依赖不受影响。
 *     <p>多实例跨进程广播为 TODO(#34) 槽位,见 NotificationSseRegistry。
 */
@Slf4j
@Service
public class NotificationSseService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 心跳帧间隔:防代理/网关空闲断连(计划书拍板 30s;配套 mvc async request-timeout=-1) */
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final String HEARTBEAT_PAYLOAD = "{\"type\":\"HEARTBEAT\"}";

    private final NotificationSseRegistry registry;
    private final boolean enabled;

    public NotificationSseService(NotificationSseRegistry registry,
                                  @Value("${erp.notify.sse.enabled:true}") boolean enabled) {
        this.registry = registry;
        this.enabled = enabled;
    }

    /** 通道开关(订阅端点据此回 404 语义) */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 建立订阅流:注册表领 sink(订阅前到达的通知帧在 buffer 里不丢)+ 30s 心跳合并;
     * 连接终态(取消/错误/完成)统一在 doFinally 注销(注销即完成流),断连即清理防 Half-open 泄漏
     */
    public Flux<ServerSentEvent<String>> subscribe(Long userId) {
        Sinks.Many<ServerSentEvent<String>> sink = registry.register(userId);
        Flux<ServerSentEvent<String>> heartbeat = Flux.interval(HEARTBEAT_INTERVAL)
                .map(tick -> ServerSentEvent.builder(HEARTBEAT_PAYLOAD).build());
        return Flux.merge(sink.asFlux(), heartbeat)
                .doFinally(signal -> registry.unregister(userId, sink));
    }

    /** pushAllUsers 出口唯一消费位:AFTER_COMMIT 相位(提交后才推帧),异常吞掉只记日志不阻断 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotifyPushed(NotifyPushedEvent event) {
        if (!enabled) {
            return;
        }
        try {
            registry.broadcast(ServerSentEvent.builder(OBJECT_MAPPER.writeValueAsString(event)).build());
        } catch (Exception e) {
            log.warn("SSE 通知推帧失败(失败不阻断站内通知): {}", e.getMessage());
        }
    }
}
