package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : SP-API Reports v0(2021-06-30)listing 报表客户端(#3 联调预备骨架):
 *         - 选型拍板(docs/04):listing 全量同步走 Reports **GET_MERCHANT_LISTINGS_ALL_DATA**
 *           (全量快照 flat file)而非 Listings Items API——后者仅单 SKU get/put/patch/delete 无枚举能力,
 *           拿不到"店铺全部 listing";报表全量 + shop_product uk upsert(saveUnifiedProduct,铁律 5)
 *           天然幂等,PRODUCT 游标退化为拉取频率控制(每次全量,无需增量窗口);
 *         - 报表异步三步:POST createReport → 轮询 getReport 至 DONE(platform 侧生成约 15~60 分钟,
 *           轮询间隔可配,防御轮询上限)→ getReportDocument 取下载地址;文档为 S3 预签名 URL,
 *           **下载请求不走 SigV4**(鉴权在 URL,仅 getReportDocument 走签名);
 *         - 压缩:compressionAlgorithm=GZIP 时解压,其余直接按 UTF-8 文本(TSV flat file);
 *         - 请求形态同 SpApiOrdersClient(SpigV4 签名 service=execute-api,POST body 参与签名载荷,
 *           Content-Type 仅随请求发出不参与签名——SP-API 规范头只签 host/x-amz-date/security-token);
 *         - 单测 JDK HttpServer 假服务不出网(AIR,docs/07 §10);异常消息只带 HTTP 状态码(docs/07 §7)
 */
public class SpApiReportsClient {

    private static final String REPORTS_PATH = "/reports/2021-06-30/reports";
    private static final String DOCUMENTS_PATH = "/reports/2021-06-30/documents/";
    private static final String SERVICE = "execute-api";
    /** listing 全量快照报表(官方报表类型字面量) */
    private static final String LISTING_REPORT_TYPE = "GET_MERCHANT_LISTINGS_ALL_DATA";
    /** 防御轮询上限:超限视为平台侧异常中止本轮(拉单重试下轮),防调度线程悬挂 */
    private static final int MAX_POLLS = 60;

    private final URI baseUri;
    private final String region;
    private final String marketplaceIds;
    private final Clock clock;
    private final Duration pollInterval;
    private final RestClient restClient;
    private final SpApiSigner signer = new SpApiSigner();

    public SpApiReportsClient(String baseUrl, String region, String marketplaceIds,
                              Clock clock, Duration pollInterval) {
        this.baseUri = URI.create(baseUrl);
        this.region = region;
        this.marketplaceIds = marketplaceIds;
        this.clock = clock;
        this.pollInterval = pollInterval;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** 配置的站点 ID(createReport 单站点,值即 erp.adapter.amazon.marketplace-ids 配置原样);币种推导入口见 AmazonMarketplace */
    public String marketplaceId() {
        return marketplaceIds;
    }

    /** 第一步:创建 listing 全量报表,返回 reportId(报表数据窗口参数不传 = 当前全量快照) */
    public String requestListingReport(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials) {
        if (StrUtil.isBlank(marketplaceIds)) {
            throw new IllegalStateException("未配置 erp.adapter.amazon.marketplace-ids,无法创建站点报表");
        }
        String body = "{\"reportType\":\"" + LISTING_REPORT_TYPE + "\",\"marketplaceIds\":[\"" + marketplaceIds + "\"]}";
        SpApiSigner.SignedHeaders signed = sign("POST", REPORTS_PATH, Map.of(), body, awsCredentials);
        JsonNode payload = execute("POST", REPORTS_PATH + "?" + signed.canonicalQueryString(), body, signed,
                lwaAccessToken, awsCredentials);
        String reportId = payload.path("reportId").asText(null);
        if (StrUtil.isBlank(reportId)) {
            throw new IllegalStateException("createReport 响应缺 reportId");
        }
        return reportId;
    }

    /**
     * 第二步:轮询 getReport 至 DONE,返回 reportDocumentId;
     * CANCELLED/FATAL 及超限抛异常(拉单重试下轮);IN_PROGRESS/IN_QUEUE 继续等
     */
    public String awaitListingReportDocument(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials,
                                             String reportId) {
        String path = REPORTS_PATH + "/" + reportId;
        for (int poll = 1; poll <= MAX_POLLS; poll++) {
            SpApiSigner.SignedHeaders signed = sign("GET", path, Map.of(), null, awsCredentials);
            JsonNode payload = execute("GET", path + "?" + signed.canonicalQueryString(), null, signed,
                    lwaAccessToken, awsCredentials);
            String status = payload.path("processingStatus").asText("");
            switch (status) {
                case "DONE" -> {
                    String documentId = payload.path("reportDocumentId").asText(null);
                    if (StrUtil.isBlank(documentId)) {
                        throw new IllegalStateException("getReport DONE 但缺 reportDocumentId");
                    }
                    return documentId;
                }
                case "CANCELLED", "FATAL" -> throw new IllegalStateException("报表生成失败:processingStatus=" + status);
                default -> { /* IN_QUEUE / IN_PROGRESS 继续等 */ }
            }
            try {
                if (pollInterval.toMillis() > 0) {
                    Thread.sleep(pollInterval.toMillis());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("报表轮询等待被中断,中止本轮", e);
            }
        }
        throw new IllegalStateException("报表轮询超出防御上限(" + MAX_POLLS + " 次),中止本轮防调度线程悬挂");
    }

    /**
     * 第三步:getReportDocument 取 S3 预签名 URL → 下载(该请求不走 SigV4,鉴权在 URL)→
     * GZIP 解压 → UTF-8 TSV 文本交翻译器
     */
    public String fetchListingReportContent(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials,
                                            String documentId) {
        String path = DOCUMENTS_PATH + documentId;
        SpApiSigner.SignedHeaders signed = sign("GET", path, Map.of(), null, awsCredentials);
        JsonNode payload = execute("GET", path + "?" + signed.canonicalQueryString(), null, signed,
                lwaAccessToken, awsCredentials);
        String url = payload.path("url").asText(null);
        if (StrUtil.isBlank(url)) {
            throw new IllegalStateException("getReportDocument 响应缺 url");
        }
        byte[] bytes;
        try {
            bytes = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("报表文档下载失败:HTTP " + e.getStatusCode().value(), e);
        }
        if (bytes == null) {
            throw new IllegalStateException("报表文档下载为空");
        }
        boolean gzip = "GZIP".equalsIgnoreCase(payload.path("compressionAlgorithm").asText(""));
        try (InputStream in = gzip ? new GZIPInputStream(new ByteArrayInputStream(bytes)) : new ByteArrayInputStream(bytes)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("报表文档解压/读取失败:" + e.getMessage(), e);
        }
    }

    /** 签名(service=execute-api,时间戳取注入 Clock);查询串取签名器回传的规范串拼 URL */
    private SpApiSigner.SignedHeaders sign(String method, String path, Map<String, String> query,
                                           String payload, SpApiSigner.AwsCredentials awsCredentials) {
        return signer.sign(new SpApiSigner.SpApiRequest(method, path, new TreeMap<>(query),
                Map.of("Host", baseUri.getHost()), payload), awsCredentials, region, SERVICE, Instant.now(clock));
    }

    /** 发请求并取 payload 节点(POST 带 body;同 SpApiOrdersClient 异常与 security-token 口径) */
    private JsonNode execute(String method, String urlWithQuery, String body, SpApiSigner.SignedHeaders signed,
                             String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials) {
        JsonNode response;
        try {
            RestClient.RequestHeadersSpec<?> spec;
            if (method.equals("POST")) {
                spec = restClient.post()
                        .uri(URI.create(urlWithQuery))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(body);
            } else {
                spec = restClient.get().uri(URI.create(urlWithQuery));
            }
            spec = spec.header("Authorization", signed.authorization())
                    .header("X-Amz-Date", signed.xAmzDate())
                    .header("x-amz-access-token", lwaAccessToken);
            if (StrUtil.isNotBlank(awsCredentials.sessionToken())) {
                spec = spec.header("X-Amz-Security-Token", awsCredentials.sessionToken());
            }
            response = spec.retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("SP-API Reports 调用失败:HTTP " + e.getStatusCode().value(), e);
        }
        if (response == null || response.get("payload") == null) {
            throw new IllegalStateException("SP-API Reports 响应缺 payload");
        }
        return response.get("payload");
    }
}
