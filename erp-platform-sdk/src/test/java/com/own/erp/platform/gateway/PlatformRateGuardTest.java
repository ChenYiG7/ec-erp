package com.own.erp.platform.gateway;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.platform.PlatformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : PlatformRateGuard 单测(AIR:mock Redisson 客户端与 MockEnvironment,不依赖真实 Redis):
 *     覆盖开关/无 Redisson 直通、setRate 每 key 一次、超时 429 fail-closed、Redis 故障 fail-open 两态。
 */
class PlatformRateGuardTest {

    private ObjectProvider<RedissonClient> redissonProvider;
    private RedissonClient redissonClient;
    private RRateLimiter rateLimiter;
    private MockEnvironment environment;
    private PlatformRateGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redissonProvider = mock(ObjectProvider.class);
        redissonClient = mock(RedissonClient.class);
        rateLimiter = mock(RRateLimiter.class);
        environment = new MockEnvironment();
        guard = new PlatformRateGuard(redissonProvider, environment);

        when(redissonProvider.getIfAvailable()).thenReturn(redissonClient);
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.getName()).thenReturn("erp:ratelimit:amazon:pull:1");
        when(rateLimiter.tryAcquire(anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    @Test
    void disabledFlagBypassesRedisCompletely() {
        environment.setProperty("erp.rate.enabled", "false");

        guard.acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL);

        verifyNoInteractions(redissonProvider, redissonClient);
    }

    @Test
    void missingRedissonBeanPassesThrough() {
        when(redissonProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() -> guard.acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL));

        verifyNoInteractions(redissonClient);
    }

    @Test
    void configuresRateOncePerKeyThenAcquires() {
        // 平台桶专属间隔配置优先于默认值
        environment.setProperty("erp.rate.amazon.pull-interval-ms", "1234");

        guard.acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL);
        guard.acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL);

        verify(redissonClient, times(2)).getRateLimiter("erp:ratelimit:amazon:pull:1");
        // setRate 每 key 每 JVM 只下发一次;平滑 1 次/间隔,OVERALL 跨实例共享
        verify(rateLimiter, times(1)).setRate(RateType.OVERALL, 1L, 1234L, RateIntervalUnit.MILLISECONDS);
        verify(rateLimiter, times(2)).tryAcquire(1234L + 2000L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    void fallsBackToDefaultIntervalWithoutSpecificConfig() {
        guard.acquire(PlatformType.SHOPEE, 2L, PlatformRateGuard.BUCKET_WRITE);

        verify(rateLimiter, times(1)).setRate(RateType.OVERALL, 1L, 2000L, RateIntervalUnit.MILLISECONDS);
    }

    @Test
    void timeoutThrows429FailClosed() {
        when(rateLimiter.tryAcquire(anyLong(), any(TimeUnit.class))).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> guard.acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL));

        assertEquals(429, e.getCode());
    }

    @Test
    void redisFailureDegradesWhenFailOpen() {
        environment.setProperty("erp.rate.fail-open", "true");
        when(redissonProvider.getIfAvailable()).thenThrow(new IllegalStateException("redis down"));

        assertDoesNotThrow(() -> guard.acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL));
    }

    @Test
    void redisFailureThrowsWhenFailClosed() {
        environment.setProperty("erp.rate.fail-open", "false");
        when(redissonClient.getRateLimiter(anyString())).thenThrow(new IllegalStateException("redis down"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> guard.acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL));

        assertEquals(503, e.getCode());
        verify(rateLimiter, never()).tryAcquire(anyLong(), any(TimeUnit.class));
    }
}
