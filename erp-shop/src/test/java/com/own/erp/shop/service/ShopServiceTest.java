package com.own.erp.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.ShopReferenceApi;
import com.own.erp.platform.AdapterRegistry;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.shop.entity.Shop;
import com.own.erp.shop.mapper.ShopMapper;
import com.own.erp.shop.request.query.ShopQuery;
import com.own.erp.shop.request.command.ShopSaveRequest;
import com.own.erp.shop.response.ShopResponse;
import com.own.erp.shop.security.CryptoService;
import com.own.erp.shop.security.OAuthStateService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : ShopService 单测(AIR:mock Mapper,不依赖数据库;固定密钥构造 CryptoService,可重复)。
 *     出参模型断言基于 ShopResponse:appSecret/refreshToken 无 getter,泄漏风险编译期即封死(docs/07 §1)
 */
class ShopServiceTest {

    private static final String BASE64_KEY =
            Base64.getEncoder().encodeToString(new byte[32]);

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 固定时钟:Token 刷新"过期前 10 分钟"窗口判定可重复(docs/07 §10 AIR) */
    private static final Instant NOW = Instant.parse("2026-09-04T04:00:00Z");

    private static CryptoService cryptoService;

    private ShopMapper shopMapper;
    private OAuthStateService oauthStateService;
    private AdapterRegistry adapterRegistry;
    private PlatformClient adapter;
    private ShopProductService shopProductService;
    private PullLogService pullLogService;
    private ShopReferenceApi shopReferenceApi;
    private ShopService shopService;

    @BeforeAll
    static void setUpCrypto() {
        // 固定 32 字节密钥(全 0),仅测试夹具
        cryptoService = new CryptoService(BASE64_KEY);
    }

    @BeforeEach
    void setUp() {
        shopMapper = mock(ShopMapper.class);
        oauthStateService = mock(OAuthStateService.class);
        adapterRegistry = mock(AdapterRegistry.class);
        adapter = mock(PlatformClient.class);
        shopProductService = mock(ShopProductService.class);
        pullLogService = mock(PullLogService.class);
        shopReferenceApi = mock(ShopReferenceApi.class);
        shopService = new ShopService(shopMapper, cryptoService, oauthStateService, adapterRegistry,
                Clock.fixed(NOW, ZONE), shopProductService, pullLogService, shopReferenceApi);
    }

    /** 基础入参 builder(默认 TAOBAO 店铺),调用点链式补字段后 .build()——SaveRequest 为 record(docs/07 §1) */
    private ShopSaveRequest.ShopSaveRequestBuilder newRequest() {
        return ShopSaveRequest.builder().platform("TAOBAO").shopName("冒烟店铺");
    }

    private Shop newShop() {
        Shop shop = new Shop();
        shop.setPlatform("TAOBAO");
        shop.setShopName("冒烟店铺");
        return shop;
    }

    // ---- createShop ----

    @Test
    void createShopEncryptsCredentialsAndKeepsAppKeyPlain() {
        ShopSaveRequest request = newRequest()
                .appKey("plain-app-key").appSecret("secret-plain")
                .accessToken("token-plain").refreshToken("refresh-plain")
                .build();

        shopService.createShop(request);

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).insert(captor.capture());
        Shop saved = captor.getValue();
        assertEquals(1L, saved.getMerchantId());
        assertEquals("plain-app-key", saved.getAppKey());
        assertDecryptable(saved.getAppSecret(), "secret-plain");
        assertDecryptable(saved.getAccessToken(), "token-plain");
        assertDecryptable(saved.getRefreshToken(), "refresh-plain");
    }

    /** 可选字段:null/blank 不参与加密,原样落库(null) */
    @Test
    void createShopWithBlankCredentialsKeepsThemNull() {
        ShopSaveRequest request = newRequest().accessToken(" ").build();

        shopService.createShop(request);

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).insert(captor.capture());
        assertNull(captor.getValue().getAccessToken());
        assertNull(captor.getValue().getAppSecret());
    }

    @Test
    void createShopRejectsIllegalPlatform() {
        ShopSaveRequest request = newRequest().platform("FOO").build();
        BusinessException e = assertThrows(BusinessException.class, () -> shopService.createShop(request));
        assertTrue(e.getMessage().contains("非法平台编码"));
        verify(shopMapper, never()).insert(any(Shop.class));
    }

    // ---- updateShop ----

    /** 掩码回传(前端把 GET 的脱敏值原样提交)必须置 null 忽略,真实新值才加密覆盖;id 只认路径参数 */
    @Test
    void updateShopIgnoresMaskEchoAndEncryptsNewValue() {
        ShopSaveRequest request = newRequest()
                .accessToken("ABCDEF" + ShopService.MASK_SUFFIX).refreshToken("new-refresh-plain")
                .build();

        shopService.updateShop(9L, request);

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).updateById(captor.capture());
        Shop saved = captor.getValue();
        assertEquals(9L, saved.getId());
        assertNull(saved.getAccessToken());
        assertDecryptable(saved.getRefreshToken(), "new-refresh-plain");
    }

    /** null/blank 均置 null → MP updateById 忽略,不覆盖库内凭证 */
    @Test
    void updateShopNullAndBlankDoNotTouchCredentials() {
        ShopSaveRequest request = newRequest().accessToken("").shopName("新店名").build();

        shopService.updateShop(9L, request);

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).updateById(captor.capture());
        assertNull(captor.getValue().getAccessToken());
        assertEquals("新店名", captor.getValue().getShopName());
    }

    // ---- getShopSession ----

    @Test
    void getShopSessionDecryptsTokensAndConvertsExpireAt() {
        Shop shop = newShop();
        shop.setId(7L);
        shop.setAccessToken(cryptoService.encrypt("session-token"));
        shop.setTokenExpireAt(LocalDateTime.of(2026, 9, 2, 12, 0, 0));
        when(shopMapper.selectById(7L)).thenReturn(shop);

        ShopSession session = shopService.getShopSession(7L);

        assertEquals(7L, session.getShopId());
        assertEquals("session-token", session.getToken().getAccessToken());
        assertNull(session.getToken().getRefreshToken());
        assertEquals(LocalDateTime.of(2026, 9, 2, 12, 0, 0)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant(),
                session.getToken().getExpireAt());
    }

    @Test
    void getShopSessionRejectsMissingShopAndEmptyCredentials() {
        when(shopMapper.selectById(404L)).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class,
                () -> shopService.getShopSession(404L));
        assertEquals(404, missing.getCode());

        Shop empty = newShop();
        empty.setId(5L);
        when(shopMapper.selectById(5L)).thenReturn(empty);
        BusinessException noCred = assertThrows(BusinessException.class,
                () -> shopService.getShopSession(5L));
        assertTrue(noCred.getMessage().contains("尚未配置授权凭证"));
    }

    /** 解密失败(密钥换过/数据损坏)→ 业务异常,且消息不含密文 */
    @Test
    void getShopSessionTranslatesDecryptFailureWithoutLeakingCipher() {
        Shop shop = newShop();
        shop.setId(6L);
        shop.setAccessToken("not-a-valid-cipher@@@");
        when(shopMapper.selectById(6L)).thenReturn(shop);

        BusinessException e = assertThrows(BusinessException.class,
                () -> shopService.getShopSession(6L));
        assertTrue(e.getMessage().contains("请重新授权"));
        assertFalse(e.getMessage().contains("not-a-valid-cipher"));
    }

    // ---- OAuth 回调与授权地址(#3)----

    /** 构造已配置凭证的 Amazon 店铺(刷新/回调用例共用) */
    private Shop newAmazonShop() {
        Shop shop = newShop();
        shop.setId(7L);
        shop.setPlatform("AMAZON");
        shop.setAppKey("key-1");
        shop.setAppSecret(cryptoService.encrypt("secret-plain"));
        shop.setAccessToken(cryptoService.encrypt("old-access"));
        shop.setRefreshToken(cryptoService.encrypt("old-rt"));
        return shop;
    }

    private AuthToken newToken(String access, String refresh, Instant expireAt) {
        AuthToken token = new AuthToken();
        token.setAccessToken(access);
        token.setRefreshToken(refresh);
        token.setExpireAt(expireAt);
        return token;
    }

    @Test
    void buildAuthUrlIssuesStateAndDelegatesToAdapter() {
        Shop shop = newAmazonShop();
        when(shopMapper.selectById(7L)).thenReturn(shop);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(adapter));
        when(oauthStateService.issue(7L)).thenReturn("state-1");
        when(adapter.buildAuthUrl("http://cb", "state-1")).thenReturn("http://auth-url");

        assertEquals("http://auth-url", shopService.buildAuthUrl(7L, "http://cb"));
    }

    @Test
    void buildAuthUrlRejectsMissingShopAndUnpluggedAdapter() {
        when(shopMapper.selectById(404L)).thenReturn(null);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> shopService.buildAuthUrl(404L, "http://cb")).getCode());

        when(shopMapper.selectById(7L)).thenReturn(newAmazonShop());
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.empty());
        BusinessException e = assertThrows(BusinessException.class,
                () -> shopService.buildAuthUrl(7L, "http://cb"));
        assertTrue(e.getMessage().contains("未接入"));
    }

    @Test
    void oauthCallbackExchangesAndPersistsEncryptedToken() {
        Shop shop = newAmazonShop();
        shop.setTokenExpireAt(null);
        when(oauthStateService.verify("state-1")).thenReturn(7L);
        when(shopMapper.selectById(7L)).thenReturn(shop);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(adapter));
        Instant expire = NOW.plusSeconds(3600);
        when(adapter.exchangeToken("code-1", "http://cb", "key-1", "secret-plain"))
                .thenReturn(newToken("new-access", "new-refresh", expire));

        assertEquals(7L, shopService.handleOAuthCallback("state-1", "A1B2C3", "code-1", "http://cb"));

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).updateById(captor.capture());
        Shop saved = captor.getValue();
        assertEquals("A1B2C3", saved.getSellerId());
        assertEquals("new-access", cryptoService.decrypt(saved.getAccessToken()));
        assertEquals("new-refresh", cryptoService.decrypt(saved.getRefreshToken()));
        assertEquals(LocalDateTime.ofInstant(expire, ZONE), saved.getTokenExpireAt());
    }

    /** 平台换 Token 响应不回传 refresh_token 时不覆盖原值(列保留旧密文) */
    @Test
    void oauthCallbackKeepsOldRefreshTokenWhenAbsentInResponse() {
        Shop shop = newAmazonShop();
        when(oauthStateService.verify("state-1")).thenReturn(7L);
        when(shopMapper.selectById(7L)).thenReturn(shop);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(adapter));
        when(adapter.exchangeToken(eq("code-1"), anyString(), eq("key-1"), eq("secret-plain")))
                .thenReturn(newToken("new-access", null, NOW.plusSeconds(3600)));

        shopService.handleOAuthCallback("state-1", null, "code-1", "http://cb");

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopMapper).updateById(captor.capture());
        assertEquals("old-rt", cryptoService.decrypt(captor.getValue().getRefreshToken()));
        assertNull(captor.getValue().getSellerId());
    }

    @Test
    void oauthCallbackRejectsMissingAppCredentials() {
        Shop shop = newAmazonShop();
        shop.setAppKey(null);
        when(oauthStateService.verify("state-1")).thenReturn(7L);
        when(shopMapper.selectById(7L)).thenReturn(shop);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(adapter));

        BusinessException e = assertThrows(BusinessException.class,
                () -> shopService.handleOAuthCallback("state-1", null, "code-1", "http://cb"));
        assertTrue(e.getMessage().contains("appKey/appSecret"));
        verify(adapter, never()).exchangeToken(anyString(), anyString(), anyString(), anyString());
    }

    // ---- Token 刷新(过期前 10 分钟,#3 docs/04 定案)----

    @Test
    void refreshesTokenWhenWithinTenMinutesOfExpiry() {
        Shop shop = newAmazonShop();
        LocalDateTime nearExpiry = LocalDateTime.ofInstant(NOW.plusSeconds(300), ZONE);
        shop.setTokenExpireAt(nearExpiry);
        when(shopMapper.selectById(7L)).thenReturn(shop);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(adapter));
        Instant newExpire = NOW.plusSeconds(3600);
        when(adapter.refreshToken("old-rt", "key-1", "secret-plain"))
                .thenReturn(newToken("new-access", null, newExpire));
        when(shopMapper.casRefreshToken(eq(7L), eq(nearExpiry), any(), isNull(),
                eq(LocalDateTime.ofInstant(newExpire, ZONE)))).thenReturn(1);

        ShopSession session = shopService.getShopSession(7L);

        // 会话即新令牌;LWA 不轮换,refresh_token 会话侧保留原值
        assertEquals("new-access", session.getToken().getAccessToken());
        assertEquals("old-rt", session.getToken().getRefreshToken());
        assertEquals(newExpire, session.getToken().getExpireAt());
    }

    @Test
    void skipsRefreshWhenTokenStillFresh() {
        Shop shop = newAmazonShop();
        shop.setTokenExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(7200), ZONE));
        when(shopMapper.selectById(7L)).thenReturn(shop);

        ShopSession session = shopService.getShopSession(7L);

        assertEquals("old-access", session.getToken().getAccessToken());
        verifyNoInteractions(adapterRegistry);
        verify(shopMapper, never()).casRefreshToken(any(), any(), any(), any(), any());
    }

    @Test
    void skipsRefreshSilentlyWhenAdapterNotPlugged() {
        Shop shop = newAmazonShop();
        shop.setTokenExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(300), ZONE));
        when(shopMapper.selectById(7L)).thenReturn(shop);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.empty());

        ShopSession session = shopService.getShopSession(7L);

        // #4 口径:adapter 未接入静默跳过(不报错、不刷新),会话按现状装配
        assertEquals("old-access", session.getToken().getAccessToken());
        verify(shopMapper, never()).casRefreshToken(any(), any(), any(), any(), any());
    }

    /** CAS 脱靶 = 他实例刚刷新:弃本次换发结果,回库重读装配,不再二次刷新 */
    @Test
    void casMissRereadsLatestTokenFromDb() {
        Shop stale = newAmazonShop();
        stale.setTokenExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(300), ZONE));
        Shop latest = newAmazonShop();
        latest.setAccessToken(cryptoService.encrypt("latest-access"));
        latest.setTokenExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZONE));
        when(shopMapper.selectById(7L)).thenReturn(stale, latest);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(adapter));
        when(adapter.refreshToken(anyString(), anyString(), anyString()))
                .thenReturn(newToken("raced-access", null, NOW.plusSeconds(3600)));
        when(shopMapper.casRefreshToken(any(), any(), any(), any(), any())).thenReturn(0);

        ShopSession session = shopService.getShopSession(7L);

        assertEquals("latest-access", session.getToken().getAccessToken());
        verify(adapter, org.mockito.Mockito.times(1)).refreshToken(anyString(), anyString(), anyString());
    }

    @Test
    void refreshFailureThrowsBusinessExceptionWithoutTokenLeak() {
        Shop shop = newAmazonShop();
        shop.setTokenExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(300), ZONE));
        when(shopMapper.selectById(7L)).thenReturn(shop);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(adapter));
        when(adapter.refreshToken(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("LWA 令牌接口调用失败:HTTP 400"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> shopService.getShopSession(7L));
        assertTrue(e.getMessage().contains("Token 刷新失败"));
        assertTrue(e.getMessage().contains("shopId=7"));
        assertFalse(e.getMessage().contains("old-rt"));
    }

    @Test
    void refreshRejectsMissingAppCredentials() {
        Shop shop = newAmazonShop();
        shop.setAppKey(" ");
        shop.setTokenExpireAt(LocalDateTime.ofInstant(NOW.plusSeconds(300), ZONE));
        when(shopMapper.selectById(7L)).thenReturn(shop);
        when(adapterRegistry.get(PlatformType.AMAZON)).thenReturn(Optional.of(adapter));

        BusinessException e = assertThrows(BusinessException.class,
                () -> shopService.getShopSession(7L));
        assertTrue(e.getMessage().contains("appKey/appSecret"));
        verify(adapter, never()).refreshToken(anyString(), anyString(), anyString());
    }

    // ---- 对外读出口脱敏(pageShops / getShopById / deleteShop)----

    /** 凭证表对外只回脱敏值:分页/详情两条读路径都只能拿到掩码 accessToken;
     *  appSecret/refreshToken 在 ShopResponse 上无字段无 getter,不存在断言对象(编译期封死) */
    @Test
    void pageShopsAndGetShopByIdMaskCredentialsOnEveryPath() {
        Shop listShop = newShop();
        listShop.setId(11L);
        listShop.setAccessToken("cipher-token-body");
        listShop.setAppSecret("cipher-secret-body");
        listShop.setRefreshToken("cipher-refresh-body");
        Page<Shop> page = new Page<>(1, 10);
        page.setRecords(List.of(listShop));
        when(shopMapper.selectPage(any(), any())).thenReturn(page);

        ShopQuery query = new ShopQuery();
        ShopResponse paged = shopService.pageShops(query).getRecords().get(0);
        assertEquals("cipher" + ShopService.MASK_SUFFIX, paged.accessToken());
        assertEquals(11L, paged.id());

        Shop detail = newShop();
        detail.setId(11L);
        detail.setAccessToken("cipher-token-body");
        detail.setAppSecret("cipher-secret-body");
        detail.setRefreshToken("cipher-refresh-body");
        when(shopMapper.selectById(11L)).thenReturn(detail);

        ShopResponse single = shopService.getShopById(11L);
        assertEquals("cipher" + ShopService.MASK_SUFFIX, single.accessToken());

        assertNull(shopService.getShopById(404L));
    }

    /** 分页过滤条件透传:platform/status 进 Wrapper,分页参数走 ShopQuery 钳制 */
    @Test
    void pageShopsPassesClampedPagination() {
        when(shopMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));
        ShopQuery query = new ShopQuery();
        query.setPageNo(2);
        query.setPageSize(999);

        shopService.pageShops(query);

        ArgumentCaptor<Page<Shop>> captor = ArgumentCaptor.forClass(Page.class);
        verify(shopMapper).selectPage(captor.capture(), any());
        assertEquals(2L, captor.getValue().getCurrent());
        assertEquals(500, captor.getValue().getSize());
    }

    /** 删除下沉 Service(#3 引用校验已收口):无任何引用时放行物理删除 */
    @Test
    void deleteShopDelegatesToMapperWhenNoRefs() {
        shopService.deleteShop(9L);
        verify(shopMapper).deleteById(9L);
    }

    @Test
    void deleteShopRejectedWhenListingReferenced() {
        when(shopProductService.countByShopIds(List.of(1L))).thenReturn(3L);

        assertThrows(BusinessException.class, () -> shopService.deleteShop(1L));
        verify(shopMapper, never()).deleteById(1L);
    }

    @Test
    void deleteShopRejectedWhenPullLogReferenced() {
        when(pullLogService.countByShopIds(List.of(1L))).thenReturn(2L);

        assertThrows(BusinessException.class, () -> shopService.deleteShop(1L));
        verify(shopMapper, never()).deleteById(1L);
    }

    @Test
    void deleteShopRejectedWhenOrderReferenced() {
        when(shopReferenceApi.countOrderRefs(List.of(1L))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> shopService.deleteShop(1L));
        verify(shopMapper, never()).deleteById(1L);
    }

    @Test
    void deleteShopRejectedWhenAftersaleReferenced() {
        when(shopReferenceApi.countAftersaleRefs(List.of(1L))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> shopService.deleteShop(1L));
        verify(shopMapper, never()).deleteById(1L);
    }

    /** 断言落库值为密文:不等于明文,且能用同密钥解回原文 */
    private void assertDecryptable(String cipher, String expectedPlain) {
        assertNotEquals(expectedPlain, cipher);
        assertEquals(expectedPlain, cryptoService.decrypt(cipher));
    }
}
