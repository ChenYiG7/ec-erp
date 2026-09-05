package com.own.erp.platform.adapter.amazon;

import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : AmazonClient 单测:授权地址拼装与配置守卫、拉单入口会话/凭证前置校验
 *     (AWS 密钥未配置即友好报错,单店失败隔离,禁半配置静默出脏数据);
 *     getOrders 请求形态细节在 SpApiOrdersClientTest 覆盖,此处不重复
 */
class AmazonClientTest {

    private AmazonClient client;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("Asia/Shanghai"));
        client = new AmazonClient(
                new LwaTokenClient("http://127.0.0.1:1", clock),
                new SpApiOrdersClient("http://127.0.0.1:1", "us-east-1", "ATVPDKIKX0DER", clock),
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
    void otherSpApiCallsStayBlocked() {
        ShopSession session = new ShopSession(1L, PlatformType.AMAZON, new AuthToken());
        assertThrows(UnsupportedOperationException.class,
                () -> client.pullProducts(session, Instant.EPOCH, Instant.EPOCH));
        assertThrows(UnsupportedOperationException.class,
                () -> client.pullRefunds(session, Instant.EPOCH, Instant.EPOCH));
        assertThrows(UnsupportedOperationException.class,
                () -> client.uploadTracking(session, "111-2222222-3333333", "SF123", "SF"));
    }
}
