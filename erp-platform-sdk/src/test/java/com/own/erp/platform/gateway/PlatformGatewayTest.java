package com.own.erp.platform.gateway;

import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : PlatformGateway / AdapterRegistry 装饰单测:
 *     数据面(拉取/回写)必先取限流许可,授权面(授权跳转/换 Token/刷新)直通不限流;
 *     注册表有守卫即包装、无守卫退回裸实现。
 */
class PlatformGatewayTest {

    private static final Instant START = Instant.parse("2026-09-04T00:00:00Z");

    private PlatformClient delegate;
    private PlatformRateGuard rateGuard;
    private ShopSession session;
    private PlatformGateway gateway;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        delegate = mock(PlatformClient.class);
        rateGuard = mock(PlatformRateGuard.class);
        when(delegate.platform()).thenReturn(PlatformType.AMAZON);
        gateway = new PlatformGateway(delegate, rateGuard);
        session = new ShopSession(1L, PlatformType.AMAZON, new AuthToken());
    }

    @Test
    void pullMethodsAcquirePermitBeforeDelegate() {
        gateway.pullOrders(session, START, START.plusSeconds(60));
        gateway.pullProducts(session, START, START.plusSeconds(60));
        gateway.pullRefunds(session, START, START.plusSeconds(60));

        verify(rateGuard, org.mockito.Mockito.times(3))
                .acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL);
        verify(delegate).pullOrders(session, START, START.plusSeconds(60));
    }

    @Test
    void writeMethodsAcquireWriteBucket() {
        gateway.uploadTracking(session, "111-222", "SF123", "SF");

        verify(rateGuard).acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_WRITE);
        verify(delegate).uploadTracking(session, "111-222", "SF123", "SF");
    }

    @Test
    void authMethodsPassThroughWithoutRateLimit() {
        gateway.buildAuthUrl("http://cb", "state-1");
        gateway.exchangeToken("code", "http://cb", "key", "secret");
        gateway.refreshToken("rt", "key", "secret");

        verifyNoInteractions(rateGuard);
        verify(delegate).buildAuthUrl("http://cb", "state-1");
        verify(delegate).exchangeToken("code", "http://cb", "key", "secret");
        verify(delegate).refreshToken("rt", "key", "secret");
    }

    @Test
    @SuppressWarnings("unchecked")
    void registryWrapsClientsWhenGuardPresent() {
        ObjectProvider<PlatformRateGuard> guardProvider = mock(ObjectProvider.class);
        when(guardProvider.getIfAvailable()).thenReturn(rateGuard);

        AdapterRegistry registry = new AdapterRegistry(List.of(delegate), guardProvider);

        PlatformClient resolved = registry.get(PlatformType.AMAZON).orElseThrow();
        // 装饰后数据面走守卫
        resolved.pullOrders(session, START, START.plusSeconds(60));
        verify(rateGuard).acquire(PlatformType.AMAZON, 1L, PlatformRateGuard.BUCKET_PULL);
    }

    @Test
    @SuppressWarnings("unchecked")
    void registryFallsBackToRawClientWhenGuardAbsent() {
        ObjectProvider<PlatformRateGuard> guardProvider = mock(ObjectProvider.class);
        when(guardProvider.getIfAvailable()).thenReturn(null);

        AdapterRegistry registry = new AdapterRegistry(List.of(delegate), guardProvider);

        assertSame(delegate, registry.get(PlatformType.AMAZON).orElseThrow());
        verify(rateGuard, never()).acquire(any(), anyLong(), anyString());
        assertEquals(PlatformType.AMAZON, gateway.platform());
    }
}
