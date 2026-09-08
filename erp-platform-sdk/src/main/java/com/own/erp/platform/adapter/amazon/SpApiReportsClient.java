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
import java.util.ArrayList;
import java.util.List;
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
    /**
     * 结算报告 V2(官方报表类型字面量,#19 2026-09-08 选型拍板):
     * **GET_V2_SETTLEMENT_REPORT_DATA_FLAT_FILE_V2**——旧版 GET_V2_SETTLEMENT_REPORT_DATA_FLAT_FILE/XML
     * 官方宣布 2026-11-11 移除,禁再引用;V2 金额三列归一(amount-type/amount-description/amount)。
     * 结算报告**不可主动创建**(官方:cannot be requested or scheduled,平台按打款周期自动生成),
     * 只能 getReports 搜索已生成报告——链路 = 列报告→取文档→下载解析,无 createReport/轮询环节
     */
    private static final String SETTLEMENT_REPORT_TYPE = "GET_V2_SETTLEMENT_REPORT_DATA_FLAT_FILE_V2";
    /** 结算报告单页拉取数:14 天一份,12 份 ≈ 半年窗口,重拉靠 uk 幂等 upsert 兜底 */
    private static final int SETTLEMENT_PAGE_SIZE = 12;
    /** 结算报告列表 NextToken 翻页防御上限(5 页 × 12 份,超限视为异常中止,同拉单翻页防御纪律) */
    private static final int MAX_LIST_PAGES = 5;
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

    /** 结算报告引用(reportId 留审计;documentId 即取文档入口,平台侧已生成完毕无需轮询) */
    public record SettlementReportRef(String reportId, String documentId) {
    }

    /**
     * 结算报告列表(#19):按 V2 报表类型 + COMPLETED 状态搜索本站点已生成报告,
     * NextToken 翻页带防御上限;documentId 缺失(平台侧异常态)即抛异常禁静默,
     * 上游拉取编排记 pull_log 走连续失败告警
     */
    public List<SettlementReportRef> listSettlementReports(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials) {
        List<SettlementReportRef> refs = new ArrayList<>();
        String nextToken = null;
        for (int page = 1; page <= MAX_LIST_PAGES; page++) {
            Map<String, String> query = new TreeMap<>();
            query.put("reportTypes", SETTLEMENT_REPORT_TYPE);
            query.put("processingStatuses", "COMPLETED");
            query.put("pageSize", String.valueOf(SETTLEMENT_PAGE_SIZE));
            if (StrUtil.isNotBlank(marketplaceIds)) {
                query.put("marketplaceIds", marketplaceIds);
            }
            if (nextToken != null) {
                query.put("nextToken", nextToken);
            }
            SpApiSigner.SignedHeaders signed = sign("GET", REPORTS_PATH, query, null, awsCredentials);
            JsonNode payload = execute("GET", REPORTS_PATH + "?" + signed.canonicalQueryString(), null, signed,
                    lwaAccessToken, awsCredentials);
            for (JsonNode report : payload.path("reports")) {
                String reportId = report.path("reportId").asText(null);
                String documentId = report.path("reportDocumentId").asText(null);
                if (StrUtil.isBlank(reportId) || StrUtil.isBlank(documentId)) {
                    throw new IllegalStateException("结算报告列表项缺 reportId/reportDocumentId,拒绝静默跳过:"
                            + report);
                }
                refs.add(new SettlementReportRef(reportId, documentId));
            }
            nextToken = payload.path("nextToken").asText(null);
            if (StrUtil.isBlank(nextToken)) {
                return refs;
            }
        }
        throw new IllegalStateException("结算报告列表翻页超出防御上限(" + MAX_LIST_PAGES + " 页),中止本轮");
    }

    /** 结算报告文档下载(同 listing 文档:S3 预签名 URL 鉴权不走 SigV4,GZIP 解压交翻译器) */
    public String fetchSettlementReportContent(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials,
                                               String documentId) {
        return downloadDocument(lwaAccessToken, awsCredentials, documentId);
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
        return downloadDocument(lwaAccessToken, awsCredentials, documentId);
    }

    /**
     * 报表文档下载共用链:getReportDocument 取 S3 预签名 URL → 下载(该请求不走 SigV4,鉴权在 URL)→
     * GZIP 解压 → UTF-8 文本交翻译器(listing/settlement 同款)
     */
    private String downloadDocument(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials,
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
