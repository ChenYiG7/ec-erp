package com.own.erp.platform.adapter.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.unified.UnifiedOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
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
 * @Date : 2026/9/5
 * @Description : SP-API Orders 客户端单测(AIR):JDK HttpServer 假服务不出网(docs/07 §10),
 *     断言 SigV4 请求形态(签名头/查询串用规范串/更新时间窗/NextToken 翻页)、明细挂载与
 *     异常只透状态码(docs/07 §7);签名算法正确性由 SpApiSignerTest 官方向量保证,此处不重复;
 *     回写面(2026-09-06):confirmShipment POST 形态/行级发运与包裹详情/入参校验先于网络调用
 */
class SpApiOrdersClientTest {

    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2026-08-29T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-08-29T00:15:00Z");
    private static final ObjectMapper TEST_MAPPER = new ObjectMapper();
    /** AWS 文档样例密钥(非真实凭证) */
    private static final SpApiSigner.AwsCredentials AWS = new SpApiSigner.AwsCredentials(
            "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null);

    private static final String ORDER_1 = """
            {"payload":{"Orders":[{"AmazonOrderId":"902-3159894-4163816","PurchaseDate":"2026-08-29T22:18:44Z",
            "OrderStatus":"Shipped","FulfillmentChannel":"MFN","OrderTotal":{"CurrencyCode":"USD","Amount":"109.36"}}]}}""";
    private static final String ORDER_1_WITH_TOKEN = """
            {"payload":{"Orders":[{"AmazonOrderId":"902-3159894-4163816","PurchaseDate":"2026-08-29T22:18:44Z",
            "OrderStatus":"Shipped","FulfillmentChannel":"MFN","OrderTotal":{"CurrencyCode":"USD","Amount":"109.36"}}],
            "NextToken":"PAGE-TOKEN-1"}}""";
    private static final String ORDER_2 = """
            {"payload":{"Orders":[{"AmazonOrderId":"111-2222222-3333333","PurchaseDate":"2026-08-29T20:00:00Z",
            "OrderStatus":"Pending","FulfillmentChannel":"MFN","OrderTotal":{"CurrencyCode":"USD","Amount":"25.00"}}]}}""";
    private static final String ITEMS = """
            {"payload":{"OrderItems":[
            {"OrderItemId":"02553626332530-1","ASIN":"B00EXAMPLE1","SellerSKU":"ERP-SKU-001",
             "Title":"Wireless Earbuds","QuantityOrdered":2,"ItemPrice":{"CurrencyCode":"USD","Amount":"41.00"}},
            {"OrderItemId":"02553626332530-2","ASIN":"B00EXAMPLE2","SellerSKU":"ERP-SKU-002",
             "Title":"USB-C Cable","QuantityOrdered":1}]}}""";

    private HttpServer server;
    private SpApiOrdersClient client;
    private final Deque<String> ordersResponses = new ConcurrentLinkedDeque<>();
    private final List<String> ordersQueries = new ArrayList<>();
    private final List<String> ordersAuthHeaders = new ArrayList<>();
    private final List<String> ordersDateHeaders = new ArrayList<>();
    private final List<String> ordersLwaTokens = new ArrayList<>();
    private final List<String> ordersSecurityTokens = new ArrayList<>();
    private final List<String> itemsQueries = new ArrayList<>();
    private volatile boolean repeatLastOrdersResponse = false;
    private volatile int ordersStatus = 200;
    private volatile String itemsResponse = ITEMS;
    private final List<String> shipmentMethods = new ArrayList<>();
    private final List<String> shipmentPaths = new ArrayList<>();
    private final List<String> shipmentBodies = new ArrayList<>();
    private final List<String> shipmentAuthHeaders = new ArrayList<>();
    private final List<String> shipmentDateHeaders = new ArrayList<>();
    private final List<String> shipmentLwaTokens = new ArrayList<>();
    private final List<String> shipmentSecurityTokens = new ArrayList<>();
    private volatile int shipmentStatus = 204;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::route);
        server.start();
        client = new SpApiOrdersClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "us-east-1", "ATVPDKIKX0DER", Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 根路径手工分流:订单列表与订单明细共用 /orders/v0/orders 前缀,回写面 = /shipment 后缀 */
    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/orders/v0/orders")) {
            handleOrders(exchange);
        } else if (path.endsWith("/shipment")) {
            handleShipment(exchange);
        } else if (path.endsWith("/orderItems")) {
            handleItems(exchange);
        } else {
            respond(exchange, 404, "{\"payload\":{}}");
        }
    }

    private void handleOrders(HttpExchange exchange) throws IOException {
        ordersQueries.add(exchange.getRequestURI().getRawQuery());
        ordersAuthHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        ordersDateHeaders.add(exchange.getRequestHeaders().getFirst("X-Amz-Date"));
        ordersLwaTokens.add(exchange.getRequestHeaders().getFirst("x-amz-access-token"));
        ordersSecurityTokens.add(exchange.getRequestHeaders().getFirst("X-Amz-Security-Token"));
        if (ordersStatus != 200) {
            respond(exchange, ordersStatus, "{\"errors\":[{\"code\":\"InvalidInput\",\"message\":\"bad parameter\"}]}");
            return;
        }
        String body = ordersResponses.poll();
        if (body == null && repeatLastOrdersResponse) {
            body = "{\"payload\":{\"Orders\":[],\"NextToken\":\"MORE\"}}";
        }
        respond(exchange, 200, body == null ? "{\"payload\":{\"Orders\":[]}}" : body);
    }

    private void handleItems(HttpExchange exchange) throws IOException {
        itemsQueries.add(exchange.getRequestURI().getRawQuery());
        respond(exchange, 200, itemsResponse);
    }

    /** MFN 确认发货:捕获请求要素,成功回 204 无响应体(Amazon 同款),非 204 回错误报文供断言禁回显 */
    private void handleShipment(HttpExchange exchange) throws IOException {
        shipmentMethods.add(exchange.getRequestMethod());
        shipmentPaths.add(exchange.getRequestURI().getPath());
        shipmentAuthHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        shipmentDateHeaders.add(exchange.getRequestHeaders().getFirst("X-Amz-Date"));
        shipmentLwaTokens.add(exchange.getRequestHeaders().getFirst("x-amz-access-token"));
        shipmentSecurityTokens.add(exchange.getRequestHeaders().getFirst("X-Amz-Security-Token"));
        shipmentBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (shipmentStatus != 204) {
            respond(exchange, shipmentStatus, "{\"errors\":[{\"code\":\"InvalidInput\",\"message\":\"bad parameter\"}]}");
            return;
        }
        exchange.sendResponseHeaders(204, -1);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void fetchesWindowSignsRequestAndMountsItems() {
        ordersResponses.add(ORDER_1);

        List<UnifiedOrder> orders = client.pullOrders("Atoken-LWA", AWS, WINDOW_START, WINDOW_END);

        assertEquals(1, ordersQueries.size());
        String query = ordersQueries.get(0);
        assertTrue(query.contains("MarketplaceIds=ATVPDKIKX0DER"), query);
        assertTrue(query.contains("LastUpdatedAfter=2026-08-29T00%3A00%3A00Z"), query);
        assertTrue(query.contains("LastUpdatedBefore=2026-08-29T00%3A15%3A00Z"), query);
        assertTrue(query.contains("MaxCount=100"), query);
        // SigV4 请求形态:scope 含 execute-api、日期头取注入时钟、LWA token 走 x-amz-access-token(不参与签名)
        String auth = ordersAuthHeaders.get(0);
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=AKIDELECTEXAMPLE/"), auth);
        assertTrue(auth.contains("/20260905/us-east-1/execute-api/aws4_request"), auth);
        assertTrue(auth.contains("SignedHeaders=host;"), auth);
        assertTrue(auth.contains("Signature="), auth);
        assertEquals("20260905T000000Z", ordersDateHeaders.get(0));
        assertEquals("Atoken-LWA", ordersLwaTokens.get(0));
        assertNull(ordersSecurityTokens.get(0));
        // 翻译与明细挂载
        UnifiedOrder order = orders.get(0);
        assertEquals("902-3159894-4163816", order.getPlatformOrderId());
        assertEquals(UnifiedOrder.OrderStatus.SHIPPED, order.getStatus());
        assertEquals(0, order.getTotalAmount().compareTo(new BigDecimal("109.36")));
        assertEquals(2, order.getItems().size());
        UnifiedOrder.Item first = order.getItems().get(0);
        assertEquals("02553626332530-1", first.getPlatformOrderItemId());
        assertEquals("ERP-SKU-001", first.getSellerSku());
        assertEquals(2, first.getQuantity());
        assertEquals(0, first.getUnitPrice().compareTo(new BigDecimal("41.00")));
        assertNull(order.getItems().get(1).getUnitPrice());
    }

    @Test
    void paginatesViaNextTokenAndStopsWhenAbsent() {
        ordersResponses.add(ORDER_1_WITH_TOKEN);
        ordersResponses.add(ORDER_2);

        List<UnifiedOrder> orders = client.pullOrders("Atoken-LWA", AWS, WINDOW_START, WINDOW_END);

        assertEquals(2, ordersQueries.size());
        assertEquals(2, orders.size());
        assertEquals("902-3159894-4163816", orders.get(0).getPlatformOrderId());
        assertEquals("111-2222222-3333333", orders.get(1).getPlatformOrderId());
        // 翻页请求:带 NextToken,不再带时间窗参数(SP-API 语义:带令牌时其余过滤参数被忽略)
        String secondQuery = ordersQueries.get(1);
        assertTrue(secondQuery.contains("NextToken=PAGE-TOKEN-1"), secondQuery);
        assertFalse(secondQuery.contains("LastUpdatedAfter"), secondQuery);
        assertFalse(secondQuery.contains("MaxCount"), secondQuery);
    }

    @Test
    void sendsSecurityTokenHeaderForTemporaryCredentials() {
        ordersResponses.add(ORDER_1);
        SpApiSigner.AwsCredentials temp = new SpApiSigner.AwsCredentials(
                "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "STS-SESSION-TOKEN-EXAMPLE");

        client.pullOrders("Atoken-LWA", temp, WINDOW_START, WINDOW_END);

        assertEquals("STS-SESSION-TOKEN-EXAMPLE", ordersSecurityTokens.get(0));
        // 临时凭证下该头必须参与签名(与请求头一致,签名器已并入规范头)
        assertTrue(ordersAuthHeaders.get(0).contains("x-amz-security-token"), ordersAuthHeaders.get(0));
    }

    @Test
    void httpErrorWrappedWithStatusOnly() {
        ordersStatus = 400;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullOrders("Atoken-LWA", AWS, WINDOW_START, WINDOW_END));

        assertTrue(exception.getMessage().contains("HTTP 400"), exception.getMessage());
        // 异常消息禁回显响应原文(docs/07 §7)
        assertFalse(exception.getMessage().contains("InvalidInput"), exception.getMessage());
    }

    @Test
    void rejectsBlankMarketplaceConfigBeforeCallingPlatform() {
        SpApiOrdersClient unconfigured = new SpApiOrdersClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), "us-east-1", "",
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> unconfigured.pullOrders("Atoken-LWA", AWS, WINDOW_START, WINDOW_END));

        assertTrue(exception.getMessage().contains("marketplace-ids"), exception.getMessage());
        assertTrue(ordersQueries.isEmpty());
    }

    @Test
    void abortsWhenNextTokenNeverEnds() {
        repeatLastOrdersResponse = true;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.pullOrders("Atoken-LWA", AWS, WINDOW_START, WINDOW_END));

        assertTrue(exception.getMessage().contains("防御上限"), exception.getMessage());
        assertEquals(50, ordersQueries.size());
        assertTrue(itemsQueries.isEmpty());
    }

    @Test
    void itemFetchCarriesSignatureAndMarketplace() {
        ordersResponses.add(ORDER_1);

        client.pullOrders("Atoken-LWA", AWS, WINDOW_START, WINDOW_END);

        assertEquals(1, itemsQueries.size());
        String itemsQuery = itemsQueries.get(0);
        assertTrue(itemsQuery.contains("MarketplaceIds=ATVPDKIKX0DER"), itemsQuery);
        assertTrue(ordersAuthHeaders.get(0).contains("execute-api"), ordersAuthHeaders.get(0));
    }

    // ---- 回写面:MFN 确认发货(2026-09-06 #3 脱机落地) ----

    /** 回传命令样板:两行明细 + 编内承运商 + 承运商名兜底并存 */
    private static PlatformShipment shipment() {
        return PlatformShipment.builder()
                .platformOrderId("902-3159894-4163816")
                .trackingNo("SF3000000001")
                .carrierCode("SF")
                .carrierName("顺丰速运")
                .shipTime(NOW)
                .items(List.of(
                        PlatformShipment.Item.builder().platformOrderItemId("02553626332530-1").quantity(2).build(),
                        PlatformShipment.Item.builder().platformOrderItemId("02553626332530-2").quantity(1).build()))
                .build();
    }

    @Test
    void confirmShipmentPostsSignedRequestWithItemQuantitiesAndPackageDetails() throws Exception {
        client.confirmShipment("Atoken-LWA", AWS, shipment());

        assertEquals("POST", shipmentMethods.get(0));
        assertEquals("/orders/v0/orders/902-3159894-4163816/shipment", shipmentPaths.get(0));
        // SigV4 请求形态:POST body 参与签名载荷,scope 含 execute-api,LWA token 走 x-amz-access-token
        String auth = shipmentAuthHeaders.get(0);
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=AKIDELECTEXAMPLE/"), auth);
        assertTrue(auth.contains("/20260905/us-east-1/execute-api/aws4_request"), auth);
        assertTrue(auth.contains("SignedHeaders=host;"), auth);
        assertEquals("20260905T000000Z", shipmentDateHeaders.get(0));
        assertEquals("Atoken-LWA", shipmentLwaTokens.get(0));
        // 请求体:marketplaceId + 行级发运 + 包裹详情(运单号/承运商/shipDate ISO8601)
        JsonNode body = TEST_MAPPER.readTree(shipmentBodies.get(0));
        assertEquals("ATVPDKIKX0DER", body.path("marketplaceId").asText());
        JsonNode orderItems = body.path("shipment").path("orderItems");
        assertEquals(2, orderItems.size());
        assertEquals("02553626332530-1", orderItems.get(0).path("orderItemId").asText());
        assertEquals(2, orderItems.get(0).path("quantity").asInt());
        assertEquals(1, orderItems.get(1).path("quantity").asInt());
        JsonNode packageDetails = body.path("shipment").path("packageDetails");
        assertEquals("SF3000000001", packageDetails.path("trackingNumber").asText());
        assertEquals("SF", packageDetails.path("carrierCode").asText());
        assertEquals("顺丰速运", packageDetails.path("carrierName").asText());
        assertEquals("2026-09-05T00:00:00Z", packageDetails.path("shipDate").asText());
    }

    @Test
    void confirmShipmentOmitsBlankCarrierCodeAndKeepsNameFallback() throws Exception {
        // 编外承运商:carrierCode 空不产出字段(禁空串脏值),carrierName 兜底
        client.confirmShipment("Atoken-LWA", AWS, PlatformShipment.builder()
                .platformOrderId("902-3159894-4163816")
                .trackingNo("SF3000000001")
                .carrierCode(" ")
                .carrierName("自定义物流")
                .shipTime(NOW)
                .items(List.of(PlatformShipment.Item.builder()
                        .platformOrderItemId("02553626332530-1").quantity(1).build()))
                .build());

        JsonNode packageDetails = TEST_MAPPER.readTree(shipmentBodies.get(0))
                .path("shipment").path("packageDetails");
        assertFalse(packageDetails.has("carrierCode"), packageDetails.toString());
        assertEquals("自定义物流", packageDetails.path("carrierName").asText());
    }

    @Test
    void confirmShipmentSendsSecurityTokenForTemporaryCredentials() {
        SpApiSigner.AwsCredentials temp = new SpApiSigner.AwsCredentials(
                "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "STS-SESSION-TOKEN-EXAMPLE");

        client.confirmShipment("Atoken-LWA", temp, shipment());

        assertEquals("STS-SESSION-TOKEN-EXAMPLE", shipmentSecurityTokens.get(0));
        // 临时凭证下该头必须参与签名(与请求头一致,签名器已并入规范头)
        assertTrue(shipmentAuthHeaders.get(0).contains("x-amz-security-token"), shipmentAuthHeaders.get(0));
    }

    @Test
    void confirmShipmentHttpErrorWrappedWithStatusOnly() {
        shipmentStatus = 400;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.confirmShipment("Atoken-LWA", AWS, shipment()));

        assertTrue(exception.getMessage().contains("HTTP 400"), exception.getMessage());
        // 异常消息禁回显响应原文(docs/07 §7)
        assertFalse(exception.getMessage().contains("InvalidInput"), exception.getMessage());
    }

    @Test
    void confirmShipmentValidatesCommandBeforeCallingPlatform() {
        PlatformShipment.Item line = PlatformShipment.Item.builder()
                .platformOrderItemId("02553626332530-1").quantity(1).build();
        PlatformShipment.Item zeroQty = PlatformShipment.Item.builder()
                .platformOrderItemId("02553626332530-1").quantity(0).build();

        // 逐要素缺省即拒,禁半配置出请求
        assertShipmentRejected(PlatformShipment.builder().trackingNo("T").carrierCode("SF")
                .shipTime(NOW).items(List.of(line)).build(), "platformOrderId");
        assertShipmentRejected(PlatformShipment.builder().platformOrderId("111").carrierCode("SF")
                .shipTime(NOW).items(List.of(line)).build(), "trackingNo");
        assertShipmentRejected(PlatformShipment.builder().platformOrderId("111").trackingNo("T")
                .carrierCode("SF").items(List.of(line)).build(), "shipTime");
        assertShipmentRejected(PlatformShipment.builder().platformOrderId("111").trackingNo("T")
                .shipTime(NOW).items(List.of(line)).build(), "物流公司");
        assertShipmentRejected(PlatformShipment.builder().platformOrderId("111").trackingNo("T")
                .carrierCode("SF").shipTime(NOW).items(List.of()).build(), "items");
        assertShipmentRejected(PlatformShipment.builder().platformOrderId("111").trackingNo("T")
                .carrierCode("SF").shipTime(NOW).items(List.of(zeroQty)).build(), "数量非正数");

        // 校验先于网络调用:假服务零请求
        assertTrue(shipmentBodies.isEmpty());
    }

    @Test
    void confirmShipmentRejectsBlankMarketplaceConfigBeforeCallingPlatform() {
        SpApiOrdersClient unconfigured = new SpApiOrdersClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), "us-east-1", "",
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> unconfigured.confirmShipment("Atoken-LWA", AWS, shipment()));

        assertTrue(exception.getMessage().contains("marketplace-ids"), exception.getMessage());
        assertTrue(shipmentBodies.isEmpty());
    }

    private void assertShipmentRejected(PlatformShipment shipment, String messagePart) {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.confirmShipment("Atoken-LWA", AWS, shipment));
        assertTrue(exception.getMessage().contains(messagePart), exception.getMessage());
    }
}
