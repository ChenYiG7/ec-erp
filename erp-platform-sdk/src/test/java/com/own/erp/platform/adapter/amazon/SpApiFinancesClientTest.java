package com.own.erp.platform.adapter.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : SP-API Finances 退款拉取客户端单测(AIR):JDK HttpServer 假服务不出网(docs/07 §10),
 *     断言记账时间窗(PostedAfter/Before)/NextToken 翻页/签名头/事件提取/防御上限/异常只透状态码;
 *     fixture 事件列表字段名取自官方 finances-api-model,真实报文联调时校准(docs/07 §8)
 */
class SpApiFinancesClientTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2026-08-30T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-30T00:15:00Z");
    /** AWS 文档样例密钥(非真实凭证) */
    private static final SpApiSigner.AwsCredentials AWS = new SpApiSigner.AwsCredentials(
            "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null);

    private static final String EVENTS_PAGE_1 = """
            {"payload":{"FinancialEvents":{"RefundEventList":[
            {"SellerOrderId":"MY-ORDER-1","OrderId":"902-3159894-4163816","PostedDate":"2026-08-30T00:05:00Z",
             "Sku":"ERP-SKU-001","QuantityPurchased":-1,
             "PrincipalAmount":{"CurrencyCode":"USD","Amount":"-41.00"}},
            {"OrderId":"111-2222222-3333333","PostedDate":"2026-08-30T00:06:00Z",
             "Sku":"ERP-SKU-002","QuantityPurchased":-2,
             "PrincipalAmount":{"CurrencyCode":"USD","Amount":"-25.00"}}],
            "ShipmentEventList":[{"OrderId":"SHOULD-BE-IGNORED"}]},
            "NextToken":"FIN-PAGE-1"}}""";
    /** 与 PAGE_1 同事件但无 NextToken(单页终局形态) */
    private static final String EVENTS_SINGLE_PAGE = """
            {"payload":{"FinancialEvents":{"RefundEventList":[
            {"SellerOrderId":"MY-ORDER-1","OrderId":"902-3159894-4163816","PostedDate":"2026-08-30T00:05:00Z",
             "Sku":"ERP-SKU-001","QuantityPurchased":-1,
             "PrincipalAmount":{"CurrencyCode":"USD","Amount":"-41.00"}},
            {"OrderId":"111-2222222-3333333","PostedDate":"2026-08-30T00:06:00Z",
             "Sku":"ERP-SKU-002","QuantityPurchased":-2,
             "PrincipalAmount":{"CurrencyCode":"USD","Amount":"-25.00"}}],
            "ShipmentEventList":[{"OrderId":"SHOULD-BE-IGNORED"}]}}}""";
    private static final String EVENTS_PAGE_2 = """
            {"payload":{"FinancialEvents":{"RefundEventList":[
            {"OrderId":"702-4444444-5555555","PostedDate":"2026-08-30T00:10:00Z",
             "Sku":"ERP-SKU-003","QuantityPurchased":-1,
             "PrincipalAmount":{"CurrencyCode":"USD","Amount":"-9.99"}}]}}}""";

    private HttpServer server;
    private SpApiFinancesClient client;
    private final Deque<String> responses = new ConcurrentLinkedDeque<>();
    private final List<String> queries = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();
    private final List<String> lwaTokens = new ArrayList<>();
    private final List<String> securityTokens = new ArrayList<>();
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::route);
        server.start();
        client = new SpApiFinancesClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "us-east-1", Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void route(HttpExchange exchange) throws IOException {
        queries.add(exchange.getRequestURI().getRawQuery());
        authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        lwaTokens.add(exchange.getRequestHeaders().getFirst("x-amz-access-token"));
        securityTokens.add(exchange.getRequestHeaders().getFirst("X-Amz-Security-Token"));
        if (status != 200) {
            respond(exchange, status, "{\"errors\":[{\"code\":\"InvalidInput\",\"message\":\"bad parameter\"}]}");
            return;
        }
        String body = responses.poll();
        respond(exchange, 200, body == null ? "{\"payload\":{\"FinancialEvents\":{}}}" : body);
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, body.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void pullsRefundWindowWithSignedRequestAndSkipsOtherEventLists() {
        responses.add(EVENTS_SINGLE_PAGE);

        List<JsonNode> events = client.pullRefundEvents("Atoken-LWA", AWS, WINDOW_START, WINDOW_END);

        assertEquals(1, queries.size());
        String query = queries.get(0);
        assertTrue(query.contains("PostedAfter=2026-08-30T00%3A00%3A00Z"), query);
        assertTrue(query.contains("PostedBefore=2026-08-30T00%3A15%3A00Z"), query);
        // 只取 RefundEventList,其余事件列表不消费(对账三期扩容)
        assertEquals(2, events.size());
        assertEquals("902-3159894-4163816", events.get(0).path("OrderId").asText());
        // SigV4 请求形态与订单拉单同口径
        assertTrue(authHeaders.get(0).contains("/20260906/us-east-1/execute-api/aws4_request"), authHeaders.get(0));
        assertEquals("Atoken-LWA", lwaTokens.get(0));
        assertNull(securityTokens.get(0));
    }

    @Test
    void paginatesViaNextTokenAndStopsWhenAbsent() {
        responses.add(EVENTS_PAGE_1);
        responses.add(EVENTS_PAGE_2);

        List<JsonNode> events = client.pullRefundEvents("Atoken-LWA", AWS, WINDOW_START, WINDOW_END);

        assertEquals(2, queries.size());
        assertEquals(3, events.size());
        String secondQuery = queries.get(1);
        assertTrue(secondQuery.contains("NextToken=FIN-PAGE-1"), secondQuery);
        // 翻页请求只带令牌,不带时间窗(SP-API 语义)
        assertFalse(secondQuery.contains("PostedAfter"), secondQuery);
    }

    @Test
    void httpErrorWrappedWithStatusOnly() {
        status = 400;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullRefundEvents("Atoken-LWA", AWS, WINDOW_START, WINDOW_END));

        assertTrue(exception.getMessage().contains("HTTP 400"), exception.getMessage());
        assertFalse(exception.getMessage().contains("InvalidInput"), exception.getMessage());
    }

    @Test
    void securityTokenHeaderSentForTemporaryCredentials() {
        responses.add(EVENTS_PAGE_1);
        SpApiSigner.AwsCredentials temp = new SpApiSigner.AwsCredentials(
                "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "STS-SESSION-TOKEN-EXAMPLE");

        client.pullRefundEvents("Atoken-LWA", temp, WINDOW_START, WINDOW_END);

        assertEquals("STS-SESSION-TOKEN-EXAMPLE", securityTokens.get(0));
        assertTrue(authHeaders.get(0).contains("x-amz-security-token"), authHeaders.get(0));
    }
}
