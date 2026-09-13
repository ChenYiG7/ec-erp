package com.own.erp.platform.adapter.douyin;

import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedRefund;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店售后/退款拉取客户端单测(AIR):断言签名公共参数随 access_token 上送、更新时间窗秒级、
 *         afterSale.List 翻页与 has_more 终止、翻译挂行级明细(金额分→元),状态/类型枚举映射;
 *         未知状态显式抛异常(禁静默吞,docs/04);HTTP 错误只透状态码;会话令牌缺失先于网络调用即拒
 */
class DouyinRefundsClientTest {

    private static final Instant NOW = Instant.ofEpochSecond(1800000000L);
    // aftersale_type=1 已发货退款→REFUND_ONLY;standard_aftersale_status=12 成功→FINISHED
    private static final String REFUND_PAGE_1 = """
            {"code":0,"msg":"success","data":{"items":[
            {"aftersale_id":"AS-1","order_id":"4200000000000000001","aftersale_type":1,"standard_aftersale_status":12,
            "refund_amount":4100,"reason":"不想要了","create_time":1724900000,"finish_time":1724900600,
            "item_list":[{"sku_id":"15000000000001","out_sku_id":"ERP-SKU-001","product_count":1,"refund_amount":4100}]}],
            "has_more":true}}""";
    // aftersale_type=3 换货→EXCHANGE;standard_aftersale_status=8 待收货→WAIT_RECEIVE;无 finish_time
    private static final String REFUND_PAGE_2 = """
            {"code":0,"msg":"success","data":{"items":[
            {"aftersale_id":"AS-2","order_id":"4200000000000000002","aftersale_type":3,"standard_aftersale_status":8,
            "refund_amount":2500,"reason":"尺码不对","create_time":1724901000,"finish_time":0,
            "item_list":[{"sku_id":"15000000000002","out_sku_id":"ERP-SKU-002","product_count":1,"refund_amount":2500}]}],
            "has_more":false}}""";

    private HttpServer server;
    private DouyinRefundsClient client;
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
        client = new DouyinRefundsClient(support);
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
                next == null ? "{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[]}}" : next);
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
    void sendsSignedQueryWithUpdateWindowAndTranslates() {
        responses.add(REFUND_PAGE_1);

        List<UnifiedRefund> refunds = client.pullRefunds(session(),
                Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L));

        String query = queries.get(0);
        assertTrue(query.contains("app_key=appkey123"), query);
        assertTrue(query.contains("method=afterSale.List"), query);
        assertTrue(query.contains("sign="), query);
        assertTrue(query.contains("access_token=Atoken-DOUYIN"), query);
        String paramJson = decodeParamJson(query);
        assertTrue(paramJson.contains("\"update_start_time\":1724900000"), paramJson);
        assertTrue(paramJson.contains("\"update_end_time\":1724901000"), paramJson);
        assertTrue(paramJson.contains("\"page\":0"), paramJson);

        UnifiedRefund refund = refunds.get(0);
        assertEquals("AS-1", refund.getPlatformRefundId());
        assertEquals("4200000000000000001", refund.getPlatformOrderId());
        assertEquals(UnifiedRefund.RefundType.REFUND_ONLY, refund.getType());
        assertEquals(UnifiedRefund.RefundStatus.FINISHED, refund.getStatus());
        assertEquals("CNY", refund.getCurrency());
        assertTrue(refund.getRefundAmount().compareTo(new BigDecimal("41.00")) == 0,
                refund.getRefundAmount().toString());
        assertNotNull(refund.getFinishTime());
        assertEquals(1, refund.getItems().size());
        UnifiedRefund.Item item = refund.getItems().get(0);
        assertEquals("ERP-SKU-001", item.getSellerSku());
        assertEquals(1, item.getQuantity());
        assertTrue(item.getRefundAmount().compareTo(new BigDecimal("41.00")) == 0,
                item.getRefundAmount().toString());
    }

    @Test
    void paginatesUntilHasMoreFalseMappingEachNode() {
        responses.add(REFUND_PAGE_1);
        responses.add(REFUND_PAGE_2);

        List<UnifiedRefund> refunds = client.pullRefunds(session(),
                Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724902000L));

        assertEquals(2, queries.size());
        assertEquals(2, refunds.size());
        assertEquals(UnifiedRefund.RefundType.EXCHANGE, refunds.get(1).getType());
        assertEquals(UnifiedRefund.RefundStatus.WAIT_RECEIVE, refunds.get(1).getStatus());
        assertNull(refunds.get(1).getFinishTime());
        assertTrue(decodeParamJson(queries.get(1)).contains("\"page\":1"), queries.get(1));
    }

    @Test
    void unknownStatusThrowsInsteadOfSilentlyMapping() {
        responses.add("{\"code\":0,\"msg\":\"success\",\"data\":{\"items\":[" +
                "{\"aftersale_id\":\"AS-9\",\"order_id\":\"O-9\",\"aftersale_type\":1," +
                "\"standard_aftersale_status\":42,\"create_time\":1724900000}]}}");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> client.pullRefunds(session(),
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("standard_aftersale_status=42"), exception.getMessage());
    }

    @Test
    void rejectsMissingSessionTokenBeforeCallingPlatform() {
        ShopSession noToken = new ShopSession(7L, PlatformType.DOUYIN, new AuthToken());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullRefunds(noToken,
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("access_token"), exception.getMessage());
        assertTrue(queries.isEmpty());
    }

    @Test
    void wrapsHttpErrorWithStatusOnly() {
        status = 503;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullRefunds(session(),
                        Instant.ofEpochSecond(1724900000L), Instant.ofEpochSecond(1724901000L)));

        assertTrue(exception.getMessage().contains("HTTP 503"), exception.getMessage());
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