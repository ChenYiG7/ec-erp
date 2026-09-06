package com.own.erp.platform.adapter.amazon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : SP-API Reports 客户端单测(AIR):JDK HttpServer 假服务不出网(docs/07 §10),
 *     断言报表三步链(createReport POST 体/轮询至 DONE/文档 URL 下载与 GZIP 解压)、
 *     轮询防御上限与失败终态、marketplace 配置守卫、异常只透状态码(docs/07 §7)
 */
class SpApiReportsClientTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    /** AWS 文档样例密钥(非真实凭证) */
    private static final SpApiSigner.AwsCredentials AWS = new SpApiSigner.AwsCredentials(
            "AKIDELECTEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null);

    private static final String LISTING_TSV = "seller-sku\titem-name\tasin1\tprice\tquantity\n"
            + "ERP-SKU-001\tWireless Earbuds\tB00EXAMPLE1\t41.00\t7\n";

    private HttpServer server;
    private SpApiReportsClient client;
    private final Deque<String> reportResponses = new ConcurrentLinkedDeque<>();
    private final List<String> requestBodies = new ArrayList<>();
    private final List<String> queries = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();
    private volatile String documentUrl = "";
    private volatile boolean documentGzip = true;
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::route);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new SpApiReportsClient(base, "us-east-1", "ATVPDKIKX0DER",
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")), Duration.ZERO);
        documentUrl = base + "/listing-report?X-Amz-Signature=presigned";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 根路径手工分流:createReport(POST)/getReport/getReportDocument/文档下载(S3 假体) */
    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();
        if (path.equals("/reports/2021-06-30/reports") && exchange.getRequestMethod().equals("POST")) {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            queries.add(query);
            authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respondJson(exchange, "{\"payload\":{\"reportId\":\"REP-123\"}}");
        } else if (path.equals("/reports/2021-06-30/reports/REP-123")) {
            queries.add(query);
            String body = reportResponses.poll();
            respondJson(exchange, body == null
                    ? "{\"payload\":{\"processingStatus\":\"DONE\",\"reportDocumentId\":\"DOC-1\"}}" : body);
        } else if (path.startsWith("/reports/2021-06-30/documents/")) {
            queries.add(query);
            respondJson(exchange, "{\"payload\":{\"url\":\"" + documentUrl + "\",\"compressionAlgorithm\":"
                    + (documentGzip ? "\"GZIP\"" : "null") + "}}");
        } else if (path.equals("/listing-report")) {
            // S3 预签名下载:鉴权在 URL,不走 SigV4
            byte[] raw = LISTING_TSV.getBytes(StandardCharsets.UTF_8);
            if (!documentGzip) {
                respond(exchange, 200, raw, "text/plain");
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                gzip.write(raw);
            }
            respond(exchange, 200, buffer.toByteArray(), "application/octet-stream");
        } else {
            respond(exchange, 404, "{\"errors\":[]}".getBytes(StandardCharsets.UTF_8), "application/json");
        }
    }

    private void respondJson(HttpExchange exchange, String body) throws IOException {
        if (status != 200) {
            respond(exchange, status, "{\"errors\":[{\"code\":\"InvalidInput\"}]}".getBytes(StandardCharsets.UTF_8),
                    "application/json");
            return;
        }
        respond(exchange, 200, body.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private void respond(HttpExchange exchange, int code, byte[] body, String contentType) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Test
    void fullListingReportChainCreatesPollsAndDownloadsGzip() {
        reportResponses.add("{\"payload\":{\"processingStatus\":\"IN_QUEUE\"}}");
        reportResponses.add("{\"payload\":{\"processingStatus\":\"IN_PROGRESS\"}}");

        String reportId = client.requestListingReport("Atoken-LWA", AWS);
        String documentId = client.awaitListingReportDocument("Atoken-LWA", AWS, reportId);
        String tsv = client.fetchListingReportContent("Atoken-LWA", AWS, documentId);

        assertEquals("REP-123", reportId);
        // createReport 请求体:报表类型 + 站点(springdoc JSON 字段名与官方模型一致)
        assertEquals("{\"reportType\":\"GET_MERCHANT_LISTINGS_ALL_DATA\",\"marketplaceIds\":[\"ATVPDKIKX0DER\"]}",
                requestBodies.get(0));
        // 轮询两页未绪后假服务默认回 DONE;getReportDocument 与下载各就位
        assertTrue(tsv.contains("ERP-SKU-001"), tsv);
        assertTrue(tsv.contains("B00EXAMPLE1"), tsv);
        // 签名头走 execute-api;下载请求鉴权在预签名 URL(无 Authorization 要求,此处不断言)
        assertTrue(authHeaders.get(0).contains("/20260906/us-east-1/execute-api/aws4_request"), authHeaders.get(0));
    }

    @Test
    void plainDocumentWithoutGzipIsPassedThrough() {
        documentGzip = false;
        reportResponses.add("{\"payload\":{\"processingStatus\":\"DONE\",\"reportDocumentId\":\"DOC-1\"}}");

        String tsv = client.fetchListingReportContent("Atoken-LWA", AWS, "DOC-1");

        assertTrue(tsv.contains("item-name\tasin1"), tsv);
    }

    @Test
    void abortedReportFailsFast() {
        reportResponses.add("{\"payload\":{\"processingStatus\":\"CANCELLED\"}}");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.awaitListingReportDocument("Atoken-LWA", AWS, "REP-123"));

        assertTrue(exception.getMessage().contains("CANCELLED"), exception.getMessage());
    }

    @Test
    void rejectsBlankMarketplaceBeforeCallingPlatform() {
        SpApiReportsClient unconfigured = new SpApiReportsClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), "us-east-1", "",
                Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")), Duration.ZERO);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> unconfigured.requestListingReport("Atoken-LWA", AWS));

        assertTrue(exception.getMessage().contains("marketplace-ids"), exception.getMessage());
        assertTrue(requestBodies.isEmpty());
    }

    @Test
    void pollLoopAbortsAtDefensiveCap() {
        // 恒回 IN_PROGRESS:60 次轮询后中止(pollInterval=0,循环即刻走完)
        reportResponses.add("{\"payload\":{\"processingStatus\":\"IN_PROGRESS\"}}");
        // 假服务 deque 耗尽后默认回 DONE——这里改为持续回 IN_PROGRESS:再压一叠
        for (int i = 0; i < 100; i++) {
            reportResponses.add("{\"payload\":{\"processingStatus\":\"IN_PROGRESS\"}}");
        }

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.awaitListingReportDocument("Atoken-LWA", AWS, "REP-123"));

        assertTrue(exception.getMessage().contains("防御上限"), exception.getMessage());
    }

    @Test
    void httpErrorWrappedWithStatusOnly() {
        status = 500;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.requestListingReport("Atoken-LWA", AWS));

        assertTrue(exception.getMessage().contains("HTTP 500"), exception.getMessage());
    }
}
