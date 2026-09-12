package com.own.erp.system.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : NotificationSseRegistry 单测(AIR,进程内真 sink 不出网):
 *     注册广播多连接 / 超上限踢最旧 / 注销后不再收到 / 断连接广播自清理(docs/07 §10)
 */
class NotificationSseRegistryTest {

    private final NotificationSseRegistry registry = new NotificationSseRegistry();

    private static ServerSentEvent<String> event(String data) {
        return ServerSentEvent.builder(data).build();
    }

    @Test
    void broadcastReachesAllRegisteredConnections() {
        Sinks.Many<ServerSentEvent<String>> first = registry.register(1L);
        Sinks.Many<ServerSentEvent<String>> second = registry.register(2L);

        registry.broadcast(event("frame-1"));
        registry.broadcast(event("frame-2"));
        // 测试终止手段:业务上由连接终态(doFinally 注销)完成流
        first.tryEmitComplete();
        second.tryEmitComplete();

        List<ServerSentEvent<String>> firstFrames = first.asFlux().collectList().block(Duration.ofSeconds(1));
        List<ServerSentEvent<String>> secondFrames = second.asFlux().collectList().block(Duration.ofSeconds(1));
        assertEquals(List.of("frame-1", "frame-2"),
                firstFrames.stream().map(ServerSentEvent::data).toList());
        assertEquals(List.of("frame-1", "frame-2"),
                secondFrames.stream().map(ServerSentEvent::data).toList());
    }

    @Test
    void evictsOldestWhenOverUserLimit() {
        Sinks.Many<ServerSentEvent<String>> oldest = registry.register(1L);
        for (int i = 0; i < NotificationSseRegistry.MAX_CONNECTIONS_PER_USER - 1; i++) {
            registry.register(1L);
        }

        // 第 6 条注册触发踢最旧:最旧连接被完成(空流),其余不受影响
        registry.register(1L);

        List<ServerSentEvent<String>> oldestFrames = oldest.asFlux().collectList().block(Duration.ofSeconds(1));
        assertEquals(List.of(), oldestFrames);
    }

    @Test
    void unregisterStopsBroadcast() {
        Sinks.Many<ServerSentEvent<String>> sink = registry.register(1L);
        registry.unregister(1L, sink);

        registry.broadcast(event("after-unregister"));

        List<ServerSentEvent<String>> frames = sink.asFlux().collectList().block(Duration.ofSeconds(1));
        assertEquals(List.of(), frames);
    }

    @Test
    void broadcastPrunesBrokenConnection() {
        Sinks.Many<ServerSentEvent<String>> broken = registry.register(1L);
        AtomicReference<ServerSentEvent<String>> received = new AtomicReference<>();
        broken.asFlux().subscribe(received::set).dispose();

        // 订阅者已取消:广播发送失败应清理该连接而非抛错,后续注册照常
        assertDoesNotThrow(() -> registry.broadcast(event("to-broken")));

        Sinks.Many<ServerSentEvent<String>> replacement = registry.register(1L);
        registry.broadcast(event("to-replacement"));
        assertEquals("to-replacement",
                replacement.asFlux().next().block(Duration.ofSeconds(1)).data());
    }
}
