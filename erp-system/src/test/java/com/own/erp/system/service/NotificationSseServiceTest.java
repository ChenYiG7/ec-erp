package com.own.erp.system.service;

import com.own.erp.system.event.NotifyPushedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : NotificationSseService 单测(AIR,registry 打桩不建真连接):
 *     开关关闭不推(通道互不干扰回归——Webhook/Mail 监听器独立不受影响)/
 *     推帧 payload 五字段齐全 / 连接终态注销 / AFTER_COMMIT 相位(docs/07 §10)
 */
class NotificationSseServiceTest {

    private NotificationSseRegistry registry;
    private NotificationSseService service;

    @BeforeEach
    void setUp() {
        registry = mock(NotificationSseRegistry.class);
        service = new NotificationSseService(registry, true);
    }

    @Test
    void skipsWhenDisabled() {
        NotificationSseService disabled = new NotificationSseService(registry, false);

        disabled.onNotifyPushed(new NotifyPushedEvent("PULL_FAIL", "标题", "内容", "ORDER", 1L));

        verifyNoInteractions(registry);
    }

    @Test
    void broadcastsJsonPayloadWithAllFields() {
        service.onNotifyPushed(new NotifyPushedEvent("PULL_FAIL", "拉单告警", "连续 3 次失败", "SHOP", 9L));

        ArgumentCaptor<ServerSentEvent<String>> captor =
                ArgumentCaptor.forClass((Class) ServerSentEvent.class);
        verify(registry).broadcast(captor.capture());
        String data = captor.getValue().data();
        assertNotNull(data);
        assertTrue(data.contains("\"notifyType\":\"PULL_FAIL\""), data);
        assertTrue(data.contains("\"title\":\"拉单告警\""), data);
        assertTrue(data.contains("\"content\":\"连续 3 次失败\""), data);
        assertTrue(data.contains("\"bizType\":\"SHOP\""), data);
        assertTrue(data.contains("\"bizId\":9"), data);
    }

    @Test
    void unregistersOnConnectionTerminate() {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(registry.register(1L)).thenReturn(sink);

        Disposable disposable = service.subscribe(1L).subscribe();
        disposable.dispose();

        verify(registry).unregister(1L, sink);
    }

    @Test
    void listenerIsAfterCommitWithFallback() throws Exception {
        Method listener = NotificationSseService.class.getMethod("onNotifyPushed", NotifyPushedEvent.class);
        TransactionalEventListener annotation = listener.getAnnotation(TransactionalEventListener.class);

        assertNotNull(annotation);
        assertEquals(TransactionPhase.AFTER_COMMIT, annotation.phase());
        assertTrue(annotation.fallbackExecution());
    }
}
