package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedProduct;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店商品同步客户端单测(AIR,JDK HttpServer 假服务):断言签名公共参数 + access_token 上送、
 *         product.listV2 分页(page=0 use_cursor=false)与 has_more 终止、翻译挂 skus(金额分→元/stock/out_sku_id/CNY);
 *         HTTP/业务错误只透状态码(docs/07 §7);会话令牌缺失先于网络调用即拒
 */
class DouyinProductClientTest {

    private static final Instant NOW = Instant.ofEpochSecond(1800000000L);
    private static final String PRODUCT_PAGE_1 = """
            {"code":0,"msg":"success","data":{"product_list":[
            {"product_id":"3500000000001","name":"无线耳机","category_id":"111","status":"on",
            "skus":[{"id":"15000000000001","spec_detail":"黑","price":4100,"stock_num":88,"out_sku_id":"ERP-SKU-001"}]}],
            "has_more":true}}""";
    private static final String PRODUCT_PAGE_2 = """
            {"code":0,"msg":"success","data":{"product_list":[
            {"product_id":"3500000000002","name":"数据线","category_id":"222","status":"off",
            "skus":[{"id":"15000000000002","spec_detail":"白","price":2500,"inventory":30,"out_sku_id":"ERP-SKU-002"}]}],
            "has_more":false}}""";

    private HttpServer server;
    private DouyinProductClient client;
    private final Deque<String> responses = new ConcurrentLinkedDeque<>();
    private final List<String> queries = new ArrayList<>();
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        DouyinApiSupport support = new DouyinApiSupport(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "appkey123", "secret123", "2", "hmac-sha256",
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
        client = new DouyinProductClient(support);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        queries.add(exchange.getRequestURI().getRawQuery());
        if (status != 200) {
            respond(exchange, status, "{\"code\":91001,\"msg\":\"external error\"}");
            return;
        }
        String next = responses.poll();
        respond(exchange, 200,
                next == null ? "{\"code\":0,\"msg\":\"success\",\"data\":{\"product_list\":[]}}" : next);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private ShopSession session() {
        AuthToken token = new AuthToken();
        token.setAccessToken("Atoken-DOUYIN");
        return new ShopSession(7L, PlatformType.DOUYIN, token);
    }

    @Test
    void sendsSignedQueryAndTranslatesSkus() {
        responses.add(PRODUCT_PAGE_1);

        List<UnifiedProduct> products = client.pullProducts(session(),
                Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L));

        String query = queries.get(0);
        assertTrue(query.contains("app_key=appkey123"), query);
        assertTrue(query.contains("method=product.listV2"), query);
        assertTrue(query.contains("sign="), query);
        assertTrue(query.contains("access_token=Atoken-DOUYIN"), query);
        String paramJson = decodeParamJson(query);
        assertTrue(paramJson.contains("\"page\":0"), paramJson);
        assertTrue(paramJson.contains("\"size\":100"), paramJson);
        assertTrue(paramJson.contains("\"use_cursor\":false"), paramJson);

        UnifiedProduct product = products.get(0);
        assertEquals("3500000000001", product.getPlatformProductId());
        assertEquals("无线耳机", product.getTitle());
        assertEquals("on", product.getStatus());
        assertEquals(1, product.getSkus().size());
        UnifiedProduct.Sku sku = product.getSkus().get(0);
        assertEquals("15000000000001", sku.getPlatformSkuId());
        assertEquals("ERP-SKU-001", sku.getSellerSku());
        assertEquals(88, sku.getStock());
        assertEquals("CNY", sku.getCurrency());
        assertTrue(sku.getPrice().compareTo(new BigDecimal("41.00")) == 0, sku.getPrice().toString());
    }

    @Test
    void paginatesUntilHasMoreFalse() {
        responses.add(PRODUCT_PAGE_1);
        responses.add(PRODUCT_PAGE_2);

        List<UnifiedProduct> products = client.pullProducts(session(),
                Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724902000L));

        assertEquals(2, queries.size());
        assertEquals(2, products.size());
        assertEquals("3500000000001", products.get(0).getPlatformProductId());
        assertEquals("3500000000002", products.get(1).getPlatformProductId());
        // 第二页 page=1,stock 回落字段 inventory
        assertTrue(decodeParamJson(queries.get(1)).contains("\"page\":1"), queries.get(1));
        assertEquals(30, products.get(1).getSkus().get(0).getStock());
    }

    @Test
    void rejectsMissingSessionTokenBeforeCallingPlatform() {
        ShopSession noToken = new ShopSession(7L, PlatformType.DOUYIN, new AuthToken());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullProducts(noToken,
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("access_token"), exception.getMessage());
        assertTrue(queries.isEmpty());
    }

    @Test
    void wrapsHttpErrorWithStatusOnly() {
        status = 503;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullProducts(session(),
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("HTTP 503"), exception.getMessage());
        assertFalse(exception.getMessage().contains("external"), exception.getMessage());
    }

    @Test
    void productMissingIdFailsWithClearMessage() {
        responses.add("{\"code\":0,\"msg\":\"success\",\"data\":{\"product_list\":[{\"name\":\"缺id\"}]}}");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> client.pullProducts(session(),
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("product_id"), exception.getMessage());
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