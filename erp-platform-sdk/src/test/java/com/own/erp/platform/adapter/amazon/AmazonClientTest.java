package com.own.erp.platform.adapter.amazon;

import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : AmazonClient 单测:授权地址拼装与配置守卫、拉单入口会话/凭证前置校验
 *     (AWS 密钥未配置即友好报错,单店失败隔离,禁半配置静默出脏数据);
 *     getOrders 请求形态细节在 SpApiOrdersClientTest 覆盖,此处不重复;
 *     listing 币种推导守卫:未收录站点先于报表创建即拒(AmazonMarketplace,2026-09-06)
 */
class AmazonClientTest {

    private AmazonClient client;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("Asia/Shanghai"));
        client = new AmazonClient(
                new LwaTokenClient("http://127.0.0.1:1", clock),
                new SpApiOrdersClient("http://127.0.0.1:1", "us-east-1", "ATVPDKIKX0DER", clock),
                new SpApiFinancesClient("http://127.0.0.1:1", "us-east-1", clock),
                new SpApiReportsClient("http://127.0.0.1:1", "us-east-1", "ATVPDKIKX0DER", clock,
                        java.time.Duration.ZERO),
                new StsTokenClient("http://127.0.0.1:1", clock),
                clock);
        ReflectionTestUtils.setField(client, "appId", "amzn1.application-oa2-client.abc");
    }

    @Test
    void buildAuthUrlCarriesAppIdAndState() {
        String url = client.buildAuthUrl("https://erp.example.com/callback", "state-xyz");

        assertTrue(url.startsWith("https://sellercentral.amazon.com/apps/authorize/consent"), url);
        assertTrue(url.contains("application_id=amzn1.application-oa2-client.abc"), url);
        assertTrue(url.contains("state=state-xyz"), url);
    }

    @Test
    void buildAuthUrlFailsWithoutConfiguredAppId() {
        ReflectionTestUtils.setField(client, "appId", "");
        assertThrows(IllegalStateException.class, () -> client.buildAuthUrl("https://cb", "state"));
    }

    @Test
    void pullOrdersRejectsSessionWithoutLwaToken() {
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, new AuthToken());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullOrders(session, Instant.EPOCH, Instant.EPOCH));

        assertTrue(exception.getMessage().contains("accessToken"), exception.getMessage());
    }

    @Test
    void pullOrdersRejectsWithoutAwsCredentialsConfigured() {
        AuthToken token = new AuthToken();
        token.setAccessToken("Atoken");
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, token);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullOrders(session, Instant.EPOCH, Instant.EPOCH));

        assertTrue(exception.getMessage().contains("AWS 密钥"), exception.getMessage());
    }

    @Test
    void pullProductsAndRefundsShareSessionGuard() {
        // 骨架接线后(#3)入口守卫同拉单口径:LWA token 缺失友好报错,不再 UnsupportedOperationException
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, new AuthToken());
        IllegalStateException products = assertThrows(IllegalStateException.class,
                () -> client.pullProducts(session, Instant.EPOCH, Instant.EPOCH));
        assertTrue(products.getMessage().contains("accessToken"), products.getMessage());
        IllegalStateException refunds = assertThrows(IllegalStateException.class,
                () -> client.pullRefunds(session, Instant.EPOCH, Instant.EPOCH));
        assertTrue(refunds.getMessage().contains("accessToken"), refunds.getMessage());
    }

    @Test
    void pullProductsRejectsUnknownMarketplaceBeforeReportRequest() {
        // 未收录站点先于报表创建即拒:不空耗平台侧 15~60 分钟报表生成(AmazonMarketplace.currencyOf)
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("Asia/Shanghai"));
        AmazonClient unknownSite = new AmazonClient(
                new LwaTokenClient("http://127.0.0.1:1", clock),
                new SpApiOrdersClient("http://127.0.0.1:1", "us-east-1", "ATVPDKIKX0DER", clock),
                new SpApiFinancesClient("http://127.0.0.1:1", "us-east-1", clock),
                new SpApiReportsClient("http://127.0.0.1:1", "us-east-1", "A0NOTREAL0XX", clock,
                        java.time.Duration.ZERO),
                new StsTokenClient("http://127.0.0.1:1", clock),
                clock);
        AuthToken token = new AuthToken();
        token.setAccessToken("Atoken");
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, token);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> unknownSite.pullProducts(session, Instant.EPOCH, Instant.EPOCH));

        assertTrue(exception.getMessage().contains("未收录"), exception.getMessage());
        assertTrue(exception.getMessage().contains("A0NOTREAL0XX"), exception.getMessage());
    }

    @Test
    void pullProductsPassesMarketplaceGateThenFailsOnMissingAwsCredentials() {
        // 已收录站点通过币种推导闸门,流程继续走 AWS 凭证校验(证明推导先行且不短路后续链路)
        AuthToken token = new AuthToken();
        token.setAccessToken("Atoken");
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, token);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullProducts(session, Instant.EPOCH, Instant.EPOCH));

        assertTrue(exception.getMessage().contains("AWS 密钥"), exception.getMessage());
    }

    @Test
    void pullRefundsRejectsWithoutAwsCredentialsConfigured() {
        AuthToken token = new AuthToken();
        token.setAccessToken("Atoken");
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, token);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullRefunds(session, Instant.EPOCH, Instant.EPOCH));

        assertTrue(exception.getMessage().contains("AWS 密钥"), exception.getMessage());
    }

    @Test
    void uploadTrackingRejectsSessionWithoutLwaToken() {
        // 回传实现落地后(2026-09-06)入口守卫同拉单口径,不再 UnsupportedOperationException
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, new AuthToken());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.uploadTracking(session, sampleShipment()));

        assertTrue(exception.getMessage().contains("accessToken"), exception.getMessage());
    }

    @Test
    void uploadTrackingRejectsWithoutAwsCredentialsConfigured() {
        AuthToken token = new AuthToken();
        token.setAccessToken("Atoken");
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, token);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.uploadTracking(session, sampleShipment()));

        assertTrue(exception.getMessage().contains("AWS 密钥"), exception.getMessage());
    }

    /** 回传命令样板(请求形态细节在 SpApiOrdersClientTest 覆盖,此处不重复) */
    private static PlatformShipment sampleShipment() {
        return PlatformShipment.builder()
                .platformOrderId("111-2222222-3333333")
                .trackingNo("SF3000000001")
                .carrierCode("SF")
                .shipTime(Instant.EPOCH)
                .items(List.of(PlatformShipment.Item.builder()
                        .platformOrderItemId("02553626332530-1").quantity(1).build()))
                .build();
    }
}
