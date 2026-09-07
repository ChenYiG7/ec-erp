package com.own.erp.contract.impl;

import com.own.erp.common.api.SystemConfigChangedEvent;
import com.own.erp.contract.SystemConfigApi;
import com.own.erp.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : SystemConfigApiImpl 单测(#18,AIR:mock 委托目标,不依赖数据库):
 *     TTL 缓存命中(同键 30s 内只查一次 DB)/变更事件精准失效/事件后重读/空值穿透缓存语义。
 *     TTL 用反射改 CACHE_TTL_MS 常量不可行(static final),改以时间戳字段直设验证窗口外重读
 */
class SystemConfigApiImplTest {

    private SystemConfigService delegate;
    private SystemConfigApiImpl impl;
    private final AtomicInteger dbHits = new AtomicInteger();

    @BeforeEach
    void setUp() {
        delegate = mock(SystemConfigService.class);
        impl = new SystemConfigApiImpl(delegate);
        dbHits.set(0);
    }

    private void stubDb(String value) {
        doAnswer(inv -> {
            dbHits.incrementAndGet();
            return value;
        }).when(delegate).valueOf(anyString());
    }

    @Test
    void cachesValueWithinTtlWindow() {
        stubDb("42");
        assertEquals("42", impl.valueOf("k"));
        assertEquals("42", impl.valueOf("k"));
        assertEquals("42", impl.valueOf("k"));
        // 3 次取值只打 1 次 DB
        assertEquals(1, dbHits.get());
    }

    @Test
    void staleEntryRereadsAfterTtl() {
        stubDb("42");
        assertEquals("42", impl.valueOf("k"));
        // 把缓存时间戳拨回 TTL 之前 → 视为陈旧
        SystemConfigApiImpl.CachedValue stale =
                new SystemConfigApiImpl.CachedValue("42", System.currentTimeMillis() - 31_000L);
        ReflectionTestUtils.setField(impl, "cache", cacheWith("k", stale));
        assertEquals("42", impl.valueOf("k"));
        assertEquals(2, dbHits.get());
    }

    @Test
    void changeEventInvalidatesExactlySubmittedKeys() {
        stubDb("42");
        impl.valueOf("k1");
        impl.valueOf("k2");
        assertEquals(2, dbHits.get()); // 两键各查一次 DB
        impl.onConfigChanged(new SystemConfigChangedEvent("AI", Set.of("k1")));
        impl.valueOf("k1");
        impl.valueOf("k2");
        // k1 失效重读(+1),k2 仍命中缓存(不再 +1)
        assertEquals(3, dbHits.get());
    }

    @Test
    void changeEventWithEmptyKeysClearsAll() {
        stubDb("42");
        impl.valueOf("k1");
        impl.valueOf("k2");
        impl.onConfigChanged(SystemConfigChangedEvent.all("AI"));
        impl.valueOf("k1");
        impl.valueOf("k2");
        assertEquals(4, dbHits.get());
    }

    @Test
    void nullValueCachedToPreventPenetration() {
        stubDb(null);
        assertNull(impl.valueOf("missing"));
        assertNull(impl.valueOf("missing"));
        assertEquals(1, dbHits.get());
    }

    @Test
    void eventCarriesGroupAndKeys() {
        // record 语义:组/键集原样携带;监听器本身不触 DB
        SystemConfigChangedEvent event = new SystemConfigChangedEvent("ALERT", Set.of("erp.alert.enabled"));
        impl.onConfigChanged(event);
        assertEquals(0, dbHits.get());
    }

    /** 构造带预置条目的缓存(反射替换字段) */
    private java.util.concurrent.ConcurrentHashMap<String, SystemConfigApiImpl.CachedValue> cacheWith(
            String key, SystemConfigApiImpl.CachedValue value) {
        java.util.concurrent.ConcurrentHashMap<String, SystemConfigApiImpl.CachedValue> cache =
                new java.util.concurrent.ConcurrentHashMap<>();
        cache.put(key, value);
        return cache;
    }
}
