package com.own.erp.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 通知 SSE 连接注册表(SSE 浏览器实时推送):进程内 userId → 连接队列,
 *     同一用户多 tab 上限 {@value #MAX_CONNECTIONS_PER_USER} 条,超限踢最旧(完成旧流促使前端重连,防泄漏);
 *     广播 = 通知渠道"不提前抽象"拍板的第四个消费者(站内落库/Webhook/邮箱/SSE),注册表只管连接,
 *     事件序列化与相位在 NotificationSseService。
 *     <p>TODO(#34) 多实例广播:本注册表为进程内 Map,单进程部署够用;扩多实例时改为 Redis pub/sub
 *     广播(频道建议 erp:notify:sse,各实例订阅后转发给本实例注册表),本类只换 broadcast 的传播介质,
 *     register/unregister/broadcast 三个口子不变——届时一并把踢旧策略与上限拍板进 TODO 条目。
 */
@Slf4j
@Component
public class NotificationSseRegistry {

    /** 同一用户多 tab 连接上限,超限踢最旧(计划书拍板默认 5) */
    static final int MAX_CONNECTIONS_PER_USER = 5;

    private final Map<Long, Deque<Sinks.Many<ServerSentEvent<String>>>> connections = new ConcurrentHashMap<>();

    /** 注册一条连接并返回其 sink(unicast + buffer:订阅前到达的通知帧缓冲不丢);超上限完成最旧连接 */
    public Sinks.Many<ServerSentEvent<String>> register(Long userId) {
        Deque<Sinks.Many<ServerSentEvent<String>>> sinks =
                connections.computeIfAbsent(userId, key -> new ConcurrentLinkedDeque<>());
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        sinks.addLast(sink);
        while (sinks.size() > MAX_CONNECTIONS_PER_USER) {
            Sinks.Many<ServerSentEvent<String>> oldest = sinks.pollFirst();
            if (oldest != null) {
                oldest.tryEmitComplete();
                log.info("SSE 连接超上限踢最旧: userId={}", userId);
            }
        }
        return sink;
    }

    /** 注销连接(连接终态回调调用)并完成其流,队列空则顺手摘 key 防慢泄漏 */
    public void unregister(Long userId, Sinks.Many<ServerSentEvent<String>> sink) {
        sink.tryEmitComplete();
        connections.computeIfPresent(userId, (key, sinks) -> {
            sinks.remove(sink);
            return sinks.isEmpty() ? null : sinks;
        });
    }

    /** 广播一帧给全部在线连接(全量扇出语义,不做用户过滤);发送失败 = 连接已断,注销并完成其流 */
    public void broadcast(ServerSentEvent<String> event) {
        for (Map.Entry<Long, Deque<Sinks.Many<ServerSentEvent<String>>>> entry : connections.entrySet()) {
            Iterator<Sinks.Many<ServerSentEvent<String>>> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                Sinks.Many<ServerSentEvent<String>> sink = iterator.next();
                if (sink.tryEmitNext(event).isFailure()) {
                    iterator.remove();
                    sink.tryEmitComplete();
                }
            }
        }
    }
}
