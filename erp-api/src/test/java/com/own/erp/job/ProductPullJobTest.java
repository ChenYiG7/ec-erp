package com.own.erp.job;

import com.own.erp.common.constant.PullConsts;
import com.own.erp.goods.service.ProductService;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.shop.service.PullLogService;
import com.own.erp.shop.service.ShopProductService;
import com.own.erp.shop.service.ShopProductSkuService;
import com.own.erp.shop.service.ShopService;
import com.own.erp.system.service.SysNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : ProductPullJob 单测(AIR:mock 协作对象 + 固定时钟),
 *     覆盖 listing 同步→自动匹配编排顺序、跨域映射传递与失败隔离;
 *     店铺锁为 Redisson LockService(#13 锁选型),释放断言 = verify(lease).close()
 */
class ProductPullJobTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-09-04T02:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 10, 0);

    private ShopService shopService;
    private AdapterRegistry adapterRegistry;
    private ShopProductService shopProductService;
    private ShopProductSkuService shopProductSkuService;
    private ProductService productService;
    private PullLogService pullLogService;
    private SysNotificationService notificationService;
    private LockService lockService;
    private LockService.Lease lease;
    private PlatformClient client;
    private ProductPullJob job;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        shopService = mock(ShopService.class);
        adapterRegistry = mock(AdapterRegistry.class);
        shopProductService = mock(ShopProductService.class);
        shopProductSkuService = mock(ShopProductSkuService.class);
        productService = mock(ProductService.class);
        pullLogService = mock(PullLogService.class);
        notificationService = mock(SysNotificationService.class);
        lockService = mock(LockService.class);
        lease = mock(LockService.Lease.class);
        client = mock(PlatformClient.class);
        when(lockService.tryAcquire(anyString())).thenReturn(lease);

        job = new ProductPullJob(shopService, adapterRegistry, shopProductService, shopProductSkuService,
                productService, pullLogService, notificationService, lockService,
                Clock.fixed(NOW_INSTANT, PullConsts.ZONE));
        ReflectionTestUtils.setField(job, "firstPullDays", 90);
    }

    @Test
    void syncsProductsThenAutoMatchsWithCrossDomainLookup() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(new ShopSession(1L, PlatformType.AMAZON, new AuthToken()));
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));

        UnifiedProduct product = new UnifiedProduct();
        product.setPlatformProductId("B0ABC123");
        UnifiedProduct.Sku sku = new UnifiedProduct.Sku();
        sku.setSellerSku("SKU-A");
        product.setSkus(List.of(sku));
        when(client.pullProducts(any(), any(), any())).thenReturn(List.of(product));
        when(productService.mapSkuCodesToIds(Set.of("SKU-A"))).thenReturn(Map.of("SKU-A", 99L));
        when(shopProductSkuService.autoMatch(eq(1L), eq(Map.of("SKU-A", 99L)))).thenReturn(1);

        job.pullProducts();

        // 以会话店铺回填 shopId;先同步落库,再跨域精确匹配回填
        assertEquals(1L, product.getShopId());
        verify(shopProductService).saveUnifiedProduct(1L, product);
        verify(productService).mapSkuCodesToIds(Set.of("SKU-A"));
        verify(shopProductSkuService).autoMatch(1L, Map.of("SKU-A", 99L));
        verify(pullLogService).recordSuccess(eq(1L), eq(PullConsts.DATA_TYPE_PRODUCT), any(), any(),
                eq(1), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        verify(lease).close();
    }

    @Test
    void skipsShopWhenPlatformAdapterMissing() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(new ShopSession(1L, PlatformType.AMAZON, new AuthToken()));
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.empty());

        job.pullProducts();

        verify(client, never()).pullProducts(any(), any(), any());
        verifyNoInteractions(pullLogService, shopProductService);
        verify(lockService, never()).tryAcquire(anyString());
    }

    /** #3:会话装配失败(含 Token 刷新失败)记 pull_log 走连续失败告警,同 OrderPullJob(docs/04) */
    @Test
    void recordsFailureWhenSessionAssemblyFails() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenThrow(new IllegalArgumentException("尚未配置授权凭证"));

        job.pullProducts();

        verify(pullLogService).recordFailure(eq(1L), eq(PullConsts.DATA_TYPE_PRODUCT), any(), any(),
                contains("尚未配置授权凭证"), eq(0L), eq(PullConsts.PULL_WAY_JOB));
        verifyNoInteractions(adapterRegistry, shopProductService);
        verify(lockService, never()).tryAcquire(anyString());
    }

    @Test
    void failureIsolatedAndCursorNotAdvanced() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(new ShopSession(1L, PlatformType.AMAZON, new AuthToken()));
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(client.pullProducts(any(), any(), any())).thenThrow(new RuntimeException("platform 500"));

        job.pullProducts();

        verify(pullLogService).recordFailure(eq(1L), eq(PullConsts.DATA_TYPE_PRODUCT), any(), any(),
                anyString(), anyLong(), eq(PullConsts.PULL_WAY_JOB));
        verify(shopProductService, never()).saveUnifiedProduct(anyLong(), any());
        verify(lease).close();
        // 判定为"未达告警条件"(mock 默认 false)时不推告警
        verify(notificationService, never()).pushAllUsers(any(), any(), any(), any(), any());
    }

    @Test
    void pushesInSiteAlertWhenFailureStreakHitsThreshold() {
        when(shopService.listEnabledShopIds()).thenReturn(List.of(1L));
        when(shopService.getShopSession(1L)).thenReturn(new ShopSession(1L, PlatformType.AMAZON, new AuthToken()));
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(client));
        when(client.pullProducts(any(), any(), any())).thenThrow(new RuntimeException("platform 500"));
        when(pullLogService.shouldAlertContinuousFailure(1L, PullConsts.DATA_TYPE_PRODUCT,
                PullConsts.FAILURE_ALERT_THRESHOLD)).thenReturn(true);

        job.pullProducts();

        verify(notificationService).pushAllUsers(eq(SysNotificationService.TYPE_PULL_FAIL),
                eq("listing 同步连续失败告警"), contains("连续失败 3 次"),
                eq(SysNotificationService.BIZ_TYPE_SHOP), eq(1L));
        verify(lease).close();
    }
}
