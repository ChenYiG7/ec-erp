package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店发货回传客户端单测(order.logisticsAdd):整单出库——order_id/logistics_code/company_code/company
 *         上送;行级明细不推进子单状态;缺单号/物流公司先于网络调用即拒 ;HTTP/业务错误上抛禁静默吞
 */
class DouyinLogisticsClientTest {

    private static final String OK = "{\"code\":0,\"msg\":\"success\",\"data\":{}}";

    private HttpServer server;
    private DouyinLogisticsClient client;
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
                Clock.fixed(Instant.ofEpochSecond(1800000000L), ZoneId.of("Asia/Shanghai")));
        client = new DouyinLogisticsClient(support);
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
        respond(exchange, 200, OK);
    }

    private void respond(HttpExchange exchange, int expectedStatus, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(expectedStatus, bytes.length);
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
    void uploadsWholeOrderTrackingWithSignedParams() throws Exception {
        client.uploadTracking(session(), PlatformShipment.builder()
                .platformOrderId("4200000000000000001")
                .trackingNo("SF3000000001")
                .carrierCode("SF")
                .carrierName("顺丰速运")
                .shipTime(Instant.ofEpochSecond(1724900000L))
                .items(List.of(PlatformShipment.Item.builder()
                        .platformOrderItemId("X-1").quantity(2).build()))
                .build());

        String query = queries.get(0);
        assertTrue(query.contains("method=order.logisticsAdd"), query);
        assertTrue(query.contains("app_key=appkey123"), query);
        assertTrue(query.contains("sign="), query);
        assertTrue(query.contains("access_token=Atoken-DOUYIN"), query);
        String paramJson = decodeParamJson(query);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = (ObjectNode) mapper.readTree(paramJson);
        assertTrue(node.has("order_id") && node.path("order_id").asText().equals("4200000000000000001"), paramJson);
        assertTrue(node.path("logistics_code").asText().equals("SF3000000001"), paramJson);
        assertTrue(node.path("company_code").asText().equals("SF"), paramJson);
        assertTrue(node.path("company").asText().equals("顺丰速运"), paramJson);
        // 整单出库:不传行级明细/发货时间(平台不需要)
        assertFalse(node.has("product_orders"), paramJson);
    }

    @Test
    void rejectsMissingIdsBeforeCallingPlatform() {
        PlatformShipment noOrderId = PlatformShipment.builder()
                .platformOrderId("").trackingNo("T").carrierCode("SF").build();
        assertThrows(IllegalStateException.class, () -> client.uploadTracking(session(), noOrderId));

        PlatformShipment noTracking = PlatformShipment.builder()
                .platformOrderId("4200000000000000001").trackingNo("").carrierCode("SF").build();
        assertThrows(IllegalStateException.class, () -> client.uploadTracking(session(), noTracking));

        PlatformShipment noCarrier = PlatformShipment.builder()
                .platformOrderId("4200000000000000001").trackingNo("T").carrierCode(" ").carrierName(" ").build();
        assertThrows(IllegalStateException.class, () -> client.uploadTracking(session(), noCarrier));

        // 校验先于网络调用:假服务零请求
        assertTrue(queries.isEmpty());
    }

    @Test
    void wrapsHttpErrorWithStatusOnly() {
        status = 503;
        PlatformShipment good = PlatformShipment.builder()
                .platformOrderId("4200000000000000001").trackingNo("T").carrierCode("SF").build();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.uploadTracking(session(), good));

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