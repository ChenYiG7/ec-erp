package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedOrder;
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
 * @Description : 抖店订单拉取客户端单测(AIR):JDK HttpServer 假服务不出网(docs/07 §10),
 *         断言签名公共参数(app_key/method/param_json/timestamp/v/sign/sign_method)随 access_token 上送、
 *         更新时间窗秒级、page 翻页与 has_more 终止、翻译挂明细;HTTP 错误只透状态码(docs/07 §7)
 */
class DouyinOrdersClientTest {

    private static final Instant NOW = Instant.ofEpochSecond(1800000000L);
    private static final String ORDER_PAGE_1 = """
            {"code":0,"msg":"success","data":{"order_list":[
            {"order_id":"P1-1","order_status":3,"create_time":1724900000,"pay_time":1724900500,
            "order_amount":10936,"post_amount":800,"order_promotion_amount":0,
            "post_receiver":{"name":"张三","province":"广东省","city":"深圳市","town":"南山区","detail":"路1号"},
            "sku_order_list":[{"sku_order_id":"P1-1-01","sku_id":"15000000000001","product_id":"3500000000001",
            "product_name":"无线耳机","spec":"黑","out_sku_id":"ERP-SKU-001","product_count":2,"price":4100}]}],
            "has_more":true}}""";
    private static final String ORDER_PAGE_2 = """
            {"code":0,"msg":"success","data":{"order_list":[
            {"order_id":"P2-1","order_status":5,"create_time":1724901000,"pay_time":1724901200,
            "order_amount":2500,"post_amount":0,"order_promotion_amount":0,
            "post_receiver":{"name":"李四","province":"浙江省","city":"杭州市","town":"西湖区","detail":"路2号"},
            "sku_order_list":[{"sku_order_id":"P2-1-01","sku_id":"15000000000002","product_id":"3500000000002",
            "product_name":"数据线","spec":"白","out_sku_id":"ERP-SKU-002","product_count":1,"price":2500}]}],
            "has_more":false}}""";

    private HttpServer server;
    private DouyinOrdersClient client;
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
        client = new DouyinOrdersClient(support);
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
                next == null ? "{\"code\":0,\"msg\":\"success\",\"data\":{\"order_list\":[]}}" : next);
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
    void sendsSignedQueryWithWindowAndTokenAndTranslates() {
        responses.add(ORDER_PAGE_1);

        List<UnifiedOrder> orders = client.pullOrders(session(),
                Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L));

        // 签名公共参数 + 会话令牌
        String query = queries.get(0);
        assertTrue(query.contains("app_key=appkey123"), query);
        assertTrue(query.contains("method=order.searchList"), query);
        assertTrue(query.contains("sign="), query);
        assertTrue(query.contains("sign_method=hmac-sha256"), query);
        assertTrue(query.contains("timestamp=1800000000"), query);
        assertTrue(query.contains("access_token=Atoken-DOUYIN"), query);
        // param_json 解码后含时间窗(秒)与分页
        String paramJson = decodeParamJson(query);
        assertTrue(paramJson.contains("\"update_time_start\":1724900000"), paramJson);
        assertTrue(paramJson.contains("\"update_time_end\":1724901000"), paramJson);
        assertTrue(paramJson.contains("\"page\":0"), paramJson);
        assertTrue(paramJson.contains("\"size\":100"), paramJson);
        // 翻译:order_status 3=已发货,金额分→元
        UnifiedOrder order = orders.get(0);
        assertEquals("P1-1", order.getPlatformOrderId());
        assertEquals(UnifiedOrder.OrderStatus.SHIPPED, order.getStatus());
        assertEquals(0, order.getTotalAmount().compareTo(new java.math.BigDecimal("109.36")));
        assertEquals(1, order.getItems().size());
    }

    @Test
    void paginatesUntilHasMoreFalse() {
        responses.add(ORDER_PAGE_1);
        responses.add(ORDER_PAGE_2);

        List<UnifiedOrder> orders = client.pullOrders(session(),
                Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724902000L));

        assertEquals(2, queries.size());
        assertEquals(2, orders.size());
        assertEquals("P1-1", orders.get(0).getPlatformOrderId());
        assertEquals("P2-1", orders.get(1).getPlatformOrderId());
        // 第二页 page=1
        assertTrue(decodeParamJson(queries.get(1)).contains("\"page\":1"), queries.get(1));
    }

    @Test
    void wrapsHttpErrorWithStatusOnly() {
        status = 503;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullOrders(session(),
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("HTTP 503"), exception.getMessage());
        assertFalse(exception.getMessage().contains("external"), exception.getMessage());
    }

    @Test
    void businessErrorCodeExposesCodeAndShortMsg() {
        responses.add("{\"code\":9,\"msg\":\"访问太频繁\",\"data\":{}}");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullOrders(session(),
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("code=9"), exception.getMessage());
        assertTrue(exception.getMessage().contains("访问太频繁"), exception.getMessage());
    }

    @Test
    void rejectsMissingSessionTokenBeforeCallingPlatform() {
        ShopSession noToken = new ShopSession(7L, PlatformType.DOUYIN, new AuthToken());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullOrders(noToken,
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("access_token"), exception.getMessage());
        assertTrue(queries.isEmpty());
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