package com.own.erp.platform.adapter.amazon;

import com.own.erp.platform.PlatformAddress;
import com.own.erp.platform.PlatformInboundPlanRequest;
import com.own.erp.platform.PlatformInboundShipment;
import com.own.erp.platform.PlatformTransportContent;
import com.own.erp.platform.gateway.RateLimitObserver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : SP-API Inbound 客户端单测(AIR):JDK HttpServer 假服务不出网(docs/07 §10),
 *     覆盖三方法(#35)请求形态/签名头/响应翻译/分批/翻页/限流头上报/异常只透状态码;
 *     fixture 为官方 fulfillment-inbound-api-model schema 推导样例,真凭证样本到位后 --force 校准(docs/07 §8)
 */
class SpApiInboundClientTest {

    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    /** AWS 文档样例密钥(非真实凭证) */
    private static final SpApiSigner.AwsCredentials AWS = new SpApiSigner.AwsCredentials(
            "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null);

    private static final String PLANS_PAYLOAD = """
            {"payload":{"InboundShipmentPlans":[
            {"ShipmentId":"FBA15ABC123","DestinationFulfillmentCenterId":"PHX7",
             "Items":[{"SellerSKU":"ERP-SKU-001","Quantity":10},{"SellerSKU":"ERP-SKU-002","Quantity":5}]}]}}""";
    private static final String SHIPMENTS_PAYLOAD = """
            {"payload":{"ShipmentData":[
            {"ShipmentId":"FBA15ABC123","DestinationFulfillmentCenterId":"PHX7",
             "ShipmentStatus":"IN_PROGRESS"}]}}""";
    private static final String SHIPMENT_ITEMS_PAYLOAD = """
            {"payload":{"ItemData":[
            {"ShipmentId":"FBA15ABC123","SellerSKU":"ERP-SKU-001","QuantityShipped":10,"QuantityReceived":8}]}}""";
    private static final String TRANSPORT_OK_PAYLOAD =
            "{\"payload\":{\"TransportResult\":{\"IsSuccess\":true,\"Message\":\"Ok\"}}}";
    private static final String TRANSPORT_REJECTED_PAYLOAD =
            "{\"payload\":{\"TransportResult\":{\"IsSuccess\":false,\"Message\":\"weight exceeds limit\"}}}";

    private HttpServer server;
    private SpApiInboundClient client;
    private RateLimitObserver observer;

    private final List<String> paths = new ArrayList<>();
    private final List<String> methods = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();
    private final List<String> lwaTokens = new ArrayList<>();
    private final List<String> securityTokens = new ArrayList<>();
    private final java.util.Deque<String> responses = new java.util.concurrent.ConcurrentLinkedDeque<>();
    /** 假服务附加的限流响应头(空串 = 不带) */
    private volatile String rateLimitHeader = "";
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::route);
        server.start();
        observer = mock(RateLimitObserver.class);
        client = new SpApiInboundClient("http://127.0.0.1:" + server.getAddress().getPort(),
                "us-east-1", "ATVPDKIKX0DER", Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")), observer);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void route(HttpExchange exchange) throws IOException {
        paths.add(exchange.getRequestURI().getPath());
        methods.add(exchange.getRequestMethod());
        queries.add(exchange.getRequestURI().getRawQuery());
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        lwaTokens.add(exchange.getRequestHeaders().getFirst("x-amz-access-token"));
        securityTokens.add(exchange.getRequestHeaders().getFirst("X-Amz-Security-Token"));
        if (status != 200) {
            respond(exchange, status, "{\"errors\":[{\"code\":\"InvalidInput\",\"message\":\"bad parameter\"}]}",
                    rateLimitHeader);
            return;
        }
        if (!responses.isEmpty()) {
            String body = responses.poll();
            respond(exchange, 200, body, rateLimitHeader);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/fba/inbound/v0/plans")) {
            respond(exchange, 200, PLANS_PAYLOAD, rateLimitHeader);
        } else if (path.equals("/fba/inbound/v0/shipments")) {
            respond(exchange, 200, SHIPMENTS_PAYLOAD, rateLimitHeader);
        } else if (path.equals("/fba/inbound/v0/shipmentItems")) {
            respond(exchange, 200, SHIPMENT_ITEMS_PAYLOAD, rateLimitHeader);
        } else if (path.endsWith("/transport")) {
            respond(exchange, 200, TRANSPORT_OK_PAYLOAD, rateLimitHeader);
        } else {
            respond(exchange, 404, "{\"errors\":[{\"code\":\"NotFound\"}]}", rateLimitHeader);
        }
    }

    private void respond(HttpExchange exchange, int code, String body, String rateLimit) throws IOException {
        if (rateLimit != null && !rateLimit.isEmpty()) {
            exchange.getResponseHeaders().add("x-amzn-RateLimit-Limit", rateLimit);
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, body.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static PlatformInboundPlanRequest planRequest() {
        return PlatformInboundPlanRequest.builder()
                .shipToCountryCode("US").labelPrepPreference("SELLER_LABEL")
                .shipFromAddress(PlatformAddress.builder()
                        .name("ERP 仓").addressLine1("No.1 Example Rd").city("Shenzhen").countryCode("CN").build())
                .items(List.of(PlatformInboundPlanRequest.Item.builder().sellerSku("ERP-SKU-001").quantity(10).build()))
                .build();
    }

    @Test
    void createsInboundPlanWithSignedRequestAndTranslatesResult() {
        List<PlatformInboundShipment> shipments =
                client.createInboundShipmentPlan("Atoken-LWA", AWS, planRequest());

        assertEquals(1, paths.size());
        assertEquals("/fba/inbound/v0/plans", paths.get(0));
        assertEquals("POST", methods.get(0));
        // 请求体经树模型构建:地址/标签偏好/计划行齐备(SellerSKU/Quantity)
        assertTrue(bodies.get(0).contains("\"ShipFromAddress\""), bodies.get(0));
        assertTrue(bodies.get(0).contains("\"LabelPrepPreference\":\"SELLER_LABEL\""), bodies.get(0));
        assertTrue(bodies.get(0).contains("\"SellerSKU\":\"ERP-SKU-001\""), bodies.get(0));
        assertTrue(bodies.get(0).contains("\"Quantity\":10"), bodies.get(0));
        // SigV4 请求形态与订单拉单同口径(service=execute-api)
        assertTrue(authHeaders.get(0).contains("/20260912/us-east-1/execute-api/aws4_request"), authHeaders.get(0));
        assertEquals("Atoken-LWA", lwaTokens.get(0));
        assertNull(securityTokens.get(0));
        // 翻译:平台拆分建议 → shipmentId + 目的地仓 + 行级计划量
        assertEquals(1, shipments.size());
        assertEquals("FBA15ABC123", shipments.get(0).shipmentId());
        assertEquals("PHX7", shipments.get(0).destinationFulfillmentCenter());
        assertEquals(2, shipments.get(0).items().size());
        assertEquals(10, shipments.get(0).items().get(0).quantityPlanned());
        assertNull(shipments.get(0).items().get(0).quantityReceived());
    }

    @Test
    void putTransportContentBuildsPartneredBodyAndReturnsIsSuccess() {
        PlatformTransportContent content = PlatformTransportContent.builder()
                .partnered(true).contactName("chenyi").contactPhone("13800000000")
                .boxes(List.of(PlatformTransportContent.Box.builder()
                        .length(new BigDecimal("60")).width(new BigDecimal("40")).height(new BigDecimal("40"))
                        .dimensionUnit("cm").weight(new BigDecimal("10")).weightUnit("kg").build()))
                .build();

        boolean success = client.putTransportContent("Atoken-LWA", AWS, "FBA15ABC123", content);

        assertTrue(success);
        assertEquals("PUT", methods.get(0));
        assertEquals("/fba/inbound/v0/shipments/FBA15ABC123/transport", paths.get(0));
        assertTrue(bodies.get(0).contains("\"PartneredSmallParcelData\""), bodies.get(0));
        assertTrue(bodies.get(0).contains("\"Contact\":{\"Name\":\"chenyi\",\"Phone\":\"13800000000\"}"), bodies.get(0));
        assertTrue(bodies.get(0).contains("\"Weight\":{\"Value\":10,\"Unit\":\"kg\"}"), bodies.get(0));
    }

    @Test
    void putTransportContentNonPartneredCarrierAndRejectedResultReturnsFalse() {
        PlatformTransportContent content = PlatformTransportContent.builder()
                .partnered(false).carrierName("UPS").build();
        responses.add(TRANSPORT_REJECTED_PAYLOAD);

        boolean success = client.putTransportContent("Atoken-LWA", AWS, "FBA15ABC123", content);

        // 平台明确拒绝(IsSuccess=false)以返回值透出,由调用方拍板处置
        assertFalse(success);
        assertTrue(bodies.get(0).contains("\"NonPartneredSmallParcelData\":{\"CarrierName\":\"UPS\"}"), bodies.get(0));
    }

    @Test
    void pullInboundShipmentsAssemblesStatusWithReceivedQuantities() {
        List<PlatformInboundShipment> shipments =
                client.pullInboundShipments("Atoken-LWA", AWS, List.of("FBA15ABC123"));

        assertEquals(2, paths.size());
        assertEquals("/fba/inbound/v0/shipments", paths.get(0));
        assertEquals("/fba/inbound/v0/shipmentItems", paths.get(1));
        assertTrue(queries.get(0).contains("ShipmentIdList=FBA15ABC123"), queries.get(0));
        assertTrue(queries.get(0).contains("QueryType=SHIPMENT"), queries.get(0));
        assertTrue(queries.get(0).contains("MarketplaceId=ATVPDKIKX0DER"), queries.get(0));
        assertEquals(1, shipments.size());
        assertEquals("IN_PROGRESS", shipments.get(0).status());
        assertEquals(1, shipments.get(0).items().size());
        assertEquals(10, shipments.get(0).items().get(0).quantityShipped());
        assertEquals(8, shipments.get(0).items().get(0).quantityReceived());
    }

    @Test
    void splitsBatchesBeyondOfficialTwentyIdsPerQuery() {
        List<String> ids = java.util.stream.IntStream.rangeClosed(1, 25)
                .mapToObj(i -> "FBA-" + i).collect(Collectors.toList());

        client.pullInboundShipments("Atoken-LWA", AWS, ids);

        // 官方 ShipmentIdList 单查上限 20 → 25 个分 2 批,每批 shipments+items 两请求
        assertEquals(4, paths.size());
        assertTrue(queries.get(0).contains("FBA-20"), queries.get(0));
        assertFalse(queries.get(0).contains("FBA-21"), queries.get(0));
        assertTrue(queries.get(2).contains("FBA-25"), queries.get(2));
    }

    @Test
    void reportsRateLimitHeaderToObserverOnSuccess() {
        rateLimitHeader = "0.0083";

        client.pullInboundShipments("Atoken-LWA", AWS, List.of("FBA15ABC123"));

        // 成功响应同样上报限流头(观测是常态校准通道,429 才观测就晚了);shipments+items 各上报一次
        ArgumentCaptor<String> operation = ArgumentCaptor.forClass(String.class);
        verify(observer, org.mockito.Mockito.times(2))
                .observe(eq(RateLimitObserver.BUCKET_PULL), operation.capture(), eq("0.0083"));
        assertEquals(List.of("getShipments", "getShipmentItems"), operation.getAllValues());
    }

    @Test
    void httpErrorWrappedWithStatusOnly() {
        status = 400;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.createInboundShipmentPlan("Atoken-LWA", AWS, planRequest()));

        assertTrue(exception.getMessage().contains("HTTP 400"), exception.getMessage());
        assertFalse(exception.getMessage().contains("bad parameter"), exception.getMessage());
    }

    @Test
    void emptyShipmentIdsReturnsEmptyWithoutRequest() {
        List<PlatformInboundShipment> shipments = client.pullInboundShipments("Atoken-LWA", AWS, List.of());

        assertTrue(shipments.isEmpty());
        assertTrue(paths.isEmpty());
        verifyNoInteractions(observer);
    }

    @Test
    void blankPlanItemsRejectedBeforeRequest() {
        PlatformInboundPlanRequest empty = PlatformInboundPlanRequest.builder()
                .shipToCountryCode("US").items(List.of()).build();

        assertThrows(IllegalStateException.class,
                () -> client.createInboundShipmentPlan("Atoken-LWA", AWS, empty));

        assertTrue(paths.isEmpty());
        verify(observer, org.mockito.Mockito.never()).observe(anyString(), anyString(), anyString());
    }
}
