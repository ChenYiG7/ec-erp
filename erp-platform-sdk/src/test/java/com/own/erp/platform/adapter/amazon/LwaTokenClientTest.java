package com.own.erp.platform.adapter.amazon;

import com.own.erp.platform.AuthToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : LWA 令牌客户端单测(AIR):JDK 自带 HttpServer 起本地假服务,不出网、不依赖外部环境;
 *     断言请求表单结构与 Token 装配/过期时间换算,并验证异常消息不回显凭证(docs/07 §7)
 */
class LwaTokenClientTest {

    private static final Instant NOW = Instant.parse("2026-09-04T02:00:00Z");
    private static final String SECRET = "sk-TEST-SECRET";

    private HttpServer server;
    private LwaTokenClient client;
    /** 记录最近一次请求体,断言表单结构 */
    private final AtomicReference<String> lastForm = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"access_token\":\"Atoken\",\"refresh_token\":\"Rtoken\",\"expires_in\":3600}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/o2/token", this::handle);
        server.start();
        client = new LwaTokenClient("http://127.0.0.1:" + server.getAddress().getPort(),
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            lastForm.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, responseBody.getBytes(StandardCharsets.UTF_8).length);
        exchange.getResponseBody().write(responseBody.getBytes(StandardCharsets.UTF_8));
        exchange.close();
    }

    @Test
    void exchangeTokenPostsAuthorizationCodeFormAndBuildsToken() {
        AuthToken token = client.exchangeToken("auth-code-123", "https://erp.example.com/api/shops/callback",
                "amzn1.application-oa2-client.app", SECRET);

        // 表单结构:grant_type/code/redirect_uri/client_id/client_secret
        String form = lastForm.get();
        assertTrue(form.contains("grant_type=authorization_code"), form);
        assertTrue(form.contains("code=auth-code-123"), form);
        assertTrue(form.contains("client_id=amzn1.application-oa2-client.app"), form);
        assertTrue(form.contains("client_secret=sk-TEST-SECRET"), form);
        // Token 装配与过期时间 = 固定时钟 + expires_in
        assertEquals("Atoken", token.getAccessToken());
        assertEquals("Rtoken", token.getRefreshToken());
        assertEquals(NOW.plusSeconds(3600), token.getExpireAt());
    }

    @Test
    void refreshTokenPostsRefreshForm() {
        AuthToken token = client.refreshToken("Rtoken-OLD", "app-id", SECRET);

        assertEquals("Atoken", token.getAccessToken());
        String form = lastForm.get();
        assertTrue(form.contains("grant_type=refresh_token"), form);
        assertTrue(form.contains("refresh_token=Rtoken-OLD"), form);
    }

    @Test
    void httpErrorIsWrappedWithoutEchoingCredentialOrBody() {
        responseStatus = 400;
        responseBody = "{\"error\":\"invalid_grant\",\"error_description\":\"bad code\"}";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.exchangeToken("bad-code", "https://erp.example.com/cb", "app-id", SECRET));

        assertTrue(exception.getMessage().contains("HTTP 400"), exception.getMessage());
        // 异常消息链上禁出现凭证与错误详情原文(docs/07 §7)
        assertTrue(!exception.getMessage().contains(SECRET), exception.getMessage());
        assertTrue(!exception.getMessage().contains("invalid_grant"), exception.getMessage());
    }
}
