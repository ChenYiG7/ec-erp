package com.own.erp.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : LockService 单测(AIR:mock RedissonClient/RLock,不依赖真 Redis):
 *     锁被占/fail-open 降级/fail-closed 跳过/释放语义(#13 锁选型定版的全部分支)
 */
class LockServiceTest {

    private ObjectProvider<RedissonClient> redissonProvider;
    private RedissonClient redissonClient;
    private RLock rLock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redissonProvider = mock(ObjectProvider.class);
        redissonClient = mock(RedissonClient.class);
        rLock = mock(RLock.class);
        when(redissonProvider.getObject()).thenReturn(redissonClient);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @Test
    void acquiresWithNamespacePrefixAndClosesByUnlock() {
        when(rLock.tryLock()).thenReturn(true);
        LockService service = new LockService(redissonProvider, true);

        LockService.Lease lease = service.tryAcquire("pull:order:1");
        assertNotNull(lease);
        // key 统一拼 erp:lock: 命名空间,与缓存等其他用途隔离
        verify(redissonClient).getLock("erp:lock:pull:order:1");
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        lease.close();
        verify(rLock).unlock();
    }

    @Test
    void returnsNullWhenLockHeldByOtherWorker() {
        when(rLock.tryLock()).thenReturn(false);

        assertNull(new LockService(redissonProvider, true).tryAcquire("pull:product:1"));
    }

    @Test
    void closeSkipsUnlockWhenNotHeldByCurrentThread() {
        when(rLock.tryLock()).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);
        LockService.Lease lease = new LockService(redissonProvider, true).tryAcquire("k");

        lease.close();

        // 非本线程持有(锁已过期/看门狗 TTL 自愈):不误释放他人锁
        verify(rLock, never()).unlock();
    }

    @Test
    void unlockFailureIsSwallowedWithTtlSelfHealing() {
        when(rLock.tryLock()).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        Mockito.doThrow(new IllegalStateException("connection reset")).when(rLock).unlock();
        LockService.Lease lease = new LockService(redissonProvider, true).tryAcquire("k");

        // 释放异常只记 warn 不外抛:锁随看门狗 TTL 自愈,不阻断调用方 finally
        lease.close();
    }

    @Test
    void degradesToNoOpLeaseWhenRedisUnavailableAndFailOpen() {
        when(redissonProvider.getObject()).thenThrow(new IllegalStateException("Redis 连接失败"));

        LockService.Lease lease = new LockService(redissonProvider, true).tryAcquire("pull:order:1");

        // fail-open(一期默认):降级租约照常放行,业务靠 uk 幂等兜底
        assertNotNull(lease);
        lease.close();
    }

    @Test
    void returnsNullWhenRedisUnavailableAndFailClosed() {
        when(redissonProvider.getObject()).thenThrow(new IllegalStateException("Redis 连接失败"));

        // fail-closed(二期多实例):锁不可用宁可跳过本轮,不重复拉撞平台风控
        assertNull(new LockService(redissonProvider, false).tryAcquire("pull:order:1"));
    }
}
