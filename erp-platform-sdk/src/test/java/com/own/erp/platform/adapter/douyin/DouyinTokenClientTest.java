package com.own.erp.platform.adapter.douyin;

import com.own.erp.platform.AuthToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店 OAuth 授权客户端单测(AIR 假服务):token.create 带 code+grant_type 现场签名、
 *         token.refresh 带 refresh_token+grant_type,返回 access_token/refresh_token/expires_in
 *         装配为 AuthToken(expireAt=签发时刻+expires_in 秒);HTTP 错误只透状态码
 */
class DouyinTokenClientTest {

    private static final Instant NOW = Instant.ofEpochSecond(1800000000L);
    private static final String TOKEN_CREATE_RESPONSE =
            "{\"code\":0,\"msg\":\"success\",\"data\":{\"access_token\":\"ACC-1\","
                    + "\"refresh_token\":\"REF-1\",\"expires_in\":172800}}";

    private HttpServer server;
    private DouyinTokenClient client;
    private final List<String> queries = new ArrayList<>();
    private volatile int status = 200;
    private volatile String response = TOKEN_CREATE_RESPONSE;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        client = new DouyinTokenClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "2", "hmac-sha256", Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        queries.add(exchange.getRequestURI().getRawQuery());
        if (status != 200) {
            respond(exchange, status, "{\"code\":91001,\"msg\":\"external\"}");
            return;
        }
        respond(exchange, 200, response);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void exchangeTokenUsesAuthorizationCodeAndReturnsAuthToken() {
        AuthToken token = client.exchangeToken("AUTH-CODE-1", "appkey123", "secret123");

        String query = queries.get(0);
        assertTrue(query.contains("method=token.create"), query);
        assertTrue(query.contains("app_key=appkey123"), query);
        assertTrue(query.contains("sign="), query);
        assertTrue(query.contains("sign_method=hmac-sha256"), query);
        // 授权码换 Token 不依赖 access_token
        assertFalse(query.contains("access_token="), query);
        String paramJson = decodeParamJson(query);
        assertTrue(paramJson.contains("\"code\":\"AUTH-CODE-1\""), paramJson);
        assertTrue(paramJson.contains("\"grant_type\":\"authorization_code\""), paramJson);
        // AuthToken 装配:expireAt = 签发时刻 + expires_in 秒
        assertEquals("ACC-1", token.getAccessToken());
        assertEquals("REF-1", token.getRefreshToken());
        assertEquals(Instant.ofEpochSecond(1800000000L + 172800L), token.getExpireAt());
    }

    @Test
    void refreshTokenRotatesWithRefreshTokenGrantType() {
        response = "{\"code\":0,\"msg\":\"success\",\"data\":{\"access_token\":\"ACC-2\","
                + "\"refresh_token\":\"REF-2\",\"expires_in\":172800}}";

        AuthToken token = client.refreshToken("REF-OLD", "appkey123", "secret123");

        String query = queries.get(0);
        assertTrue(query.contains("method=token.refresh"), query);
        String paramJson = decodeParamJson(query);
        assertTrue(paramJson.contains("\"grant_type\":\"refresh_token\""), paramJson);
        assertTrue(paramJson.contains("\"refresh_token\":\"REF-OLD\""), paramJson);
        // 刷新返回的新 refresh_token(轮换语义由授权中心落库承载)
        assertEquals("ACC-2", token.getAccessToken());
        assertEquals("REF-2", token.getRefreshToken());
    }

    @Test
    void wrapsHttpErrorWithStatusOnly() {
        status = 500;

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> client.exchangeToken("CODE", "k", "s"));

        assertTrue(exception.getMessage().contains("HTTP 500"), exception.getMessage());
        assertFalse(exception.getMessage().contains("external"), exception.getMessage());
    }

    private static String decodeParamJson(String query) {
        for (String kv : query.split("&")) {
            if (kv.startsWith("param_json=")) {
                return URLDecoder.decode(kv.substring("param_json=".length()), StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}