package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.own.erp.platform.PlatformInboundPlanRequest;
import com.own.erp.platform.PlatformInboundShipment;
import com.own.erp.platform.PlatformTransportContent;
import com.own.erp.platform.gateway.PlatformRateGuard;
import com.own.erp.platform.gateway.RateLimitObserver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : SP-API Fulfillment Inbound Shipment v0 客户端(#35 fba-shipment V2,#3 真凭证后联调):
 *         - createInboundShipmentPlan:POST /fba/inbound/v0/plans(SKU 清单 → 平台拆分建议 + 目的地 FBA 仓);
 *         - putTransportContent:PUT /fba/inbound/v0/shipments/{shipmentId}/transport(板箱信息回传);
 *         - pullInboundShipments:getShipments + getShipmentItems 两请求装配(状态 + Shipped/Received 对账量,
 *           ShipmentIdList 官方单查上限 20 个,超出分批;NextToken 翻页带防御上限);
 *         - 请求形态与 {@link SpApiOrdersClient} 同款:SpApiSigner 签名(service=execute-api,POST/PUT body
 *           参与签名载荷),查询串直接用签名器回传 canonicalQueryString 禁二次拼参,LWA accessToken 走
 *           x-amz-access-token 头;单测 JDK HttpServer 假服务不出网(AIR,docs/07 §10);
 *         - 限流真值校准:成功/失败响应都读 x-amzn-RateLimit-Limit 上报 {@link RateLimitObserver}
 *           (guard 缺位时 observer=null 静默跳过);异常消息只带 HTTP 状态码,禁回显响应原文(docs/07 §7);
 *         - 报文字段取官方 fulfillment-inbound-api-model,fixture 为 schema 推导样例,
 *           真凭证样本到位后 --force 校准一轮(docs/07 §8)
 */
public class SpApiInboundClient {

    private static final String PLANS_PATH = "/fba/inbound/v0/plans";
    private static final String SHIPMENTS_PATH = "/fba/inbound/v0/shipments";
    private static final String SHIPMENT_ITEMS_PATH = "/fba/inbound/v0/shipmentItems";
    private static final String SERVICE = "execute-api";
    /** ShipmentIdList 官方单查上限 20 个,超出分批(再由 NextToken 翻页兜底长尾) */
    private static final int MAX_IDS_PER_QUERY = 20;
    /** 翻页防御上限:NextToken 异常自旋时中止本轮(拉单重试下轮),同 SpApiOrdersClient 口径 */
    private static final int MAX_PAGES = 50;

    private final URI baseUri;
    private final String region;
    private final String marketplaceIds;
    private final Clock clock;
    private final RateLimitObserver observer;
    private final RestClient restClient;
    private final SpApiSigner signer = new SpApiSigner();

    public SpApiInboundClient(String baseUrl, String region, String marketplaceIds,
                              Clock clock, RateLimitObserver observer) {
        this.baseUri = URI.create(baseUrl);
        this.region = region;
        this.marketplaceIds = marketplaceIds;
        this.clock = clock;
        this.observer = observer;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** 生成平台发货计划(SKU 清单 → 平台拆分建议):请求体经翻译器树模型构建,响应翻译为平台中立模型 */
    public List<PlatformInboundShipment> createInboundShipmentPlan(String lwaAccessToken,
                                                                   SpApiSigner.AwsCredentials awsCredentials,
                                                                   PlatformInboundPlanRequest request) {
        if (request == null || CollUtil.isEmpty(request.items())) {
            throw new IllegalStateException("入库计划缺计划行 items,拒绝空单出请求");
        }
        String body = AmazonInboundTranslator.buildPlanRequestBody(request);
        SpApiSigner.SignedHeaders signed = sign("POST", PLANS_PATH, new TreeMap<>(), body, awsCredentials);
        JsonNode payload = execute("POST", PLANS_PATH, body, signed,
                RateLimitObserver.BUCKET_WRITE, "createInboundShipmentPlan", lwaAccessToken, awsCredentials);
        return AmazonInboundTranslator.translatePlanPayload(payload);
    }

    /** 板箱运输信息回传:返回平台侧受理结果(IsSuccess=false = 平台明确拒绝,由调用方拍板处置) */
    public boolean putTransportContent(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials,
                                       String shipmentId, PlatformTransportContent content) {
        if (StrUtil.isBlank(shipmentId)) {
            throw new IllegalStateException("板箱回传缺 shipmentId");
        }
        String path = SHIPMENTS_PATH + "/" + shipmentId + "/transport";
        String body = AmazonInboundTranslator.buildTransportRequestBody(content);
        SpApiSigner.SignedHeaders signed = sign("PUT", path, new TreeMap<>(), body, awsCredentials);
        JsonNode payload = execute("PUT", path, body, signed,
                RateLimitObserver.BUCKET_WRITE, "putTransportContent", lwaAccessToken, awsCredentials);
        return AmazonInboundTranslator.translateTransportResult(payload);
    }

    /**
     * 拉取平台收货状态(getShipments + getShipmentItems 装配):状态 + Shipped/Received 对账量,
     * 驱动 fba_shipment_diff 对账;shipmentIds 按官方单查上限分批
     */
    public List<PlatformInboundShipment> pullInboundShipments(String lwaAccessToken,
                                                              SpApiSigner.AwsCredentials awsCredentials,
                                                              List<String> shipmentIds) {
        if (CollUtil.isEmpty(shipmentIds)) {
            return List.of();
        }
        List<PlatformInboundShipment> result = new ArrayList<>();
        for (List<String> batch : CollUtil.split(shipmentIds, MAX_IDS_PER_QUERY)) {
            List<PlatformInboundShipment> shipments = fetchShipments(lwaAccessToken, awsCredentials, batch);
            Map<String, List<PlatformInboundShipment.Item>> itemsByShipment =
                    fetchShipmentItems(lwaAccessToken, awsCredentials, batch);
            for (PlatformInboundShipment shipment : shipments) {
                List<PlatformInboundShipment.Item> items = itemsByShipment.get(shipment.shipmentId());
                result.add(items == null ? shipment : new PlatformInboundShipment(shipment.shipmentId(),
                        shipment.destinationFulfillmentCenter(), shipment.status(), items));
            }
        }
        return result;
    }

    /** getShipments:QueryType=SHIPMENT + ShipmentIdList(逗号分隔)+ NextToken 翻页 */
    private List<PlatformInboundShipment> fetchShipments(String lwaAccessToken,
                                                         SpApiSigner.AwsCredentials awsCredentials,
                                                         List<String> shipmentIds) {
        List<PlatformInboundShipment> shipments = new ArrayList<>();
        String nextToken = null;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, String> query = new TreeMap<>();
            query.put("QueryType", "SHIPMENT");
            query.put("MarketplaceId", marketplaceIds);
            if (nextToken != null) {
                query.put("NextToken", nextToken);
            } else {
                query.put("ShipmentIdList", String.join(",", shipmentIds));
            }
            SpApiSigner.SignedHeaders signed = sign("GET", SHIPMENTS_PATH, query, null, awsCredentials);
            JsonNode payload = execute("GET", SHIPMENTS_PATH, null, signed,
                    RateLimitObserver.BUCKET_PULL, "getShipments", lwaAccessToken, awsCredentials);
            shipments.addAll(AmazonInboundTranslator.translateShipmentsPayload(payload));
            nextToken = payload.path("NextToken").asText(null);
            if (StrUtil.isBlank(nextToken)) {
                return shipments;
            }
        }
        throw new IllegalStateException("getShipments 翻页超出防御上限(> " + MAX_PAGES + " 页),中止防 NextToken 异常自旋");
    }

    /** getShipmentItems:对账行按 ShipmentId 分组(同 QueryType=SHIPMENT 翻页口径) */
    private Map<String, List<PlatformInboundShipment.Item>> fetchShipmentItems(String lwaAccessToken,
                                                                               SpApiSigner.AwsCredentials awsCredentials,
                                                                               List<String> shipmentIds) {
        Map<String, List<PlatformInboundShipment.Item>> itemsByShipment = new TreeMap<>();
        String nextToken = null;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, String> query = new TreeMap<>();
            query.put("QueryType", "SHIPMENT");
            query.put("MarketplaceId", marketplaceIds);
            if (nextToken != null) {
                query.put("NextToken", nextToken);
            } else {
                query.put("ShipmentIdList", String.join(",", shipmentIds));
            }
            SpApiSigner.SignedHeaders signed = sign("GET", SHIPMENT_ITEMS_PATH, query, null, awsCredentials);
            JsonNode payload = execute("GET", SHIPMENT_ITEMS_PATH, null, signed,
                    RateLimitObserver.BUCKET_PULL, "getShipmentItems", lwaAccessToken, awsCredentials);
            itemsByShipment.putAll(AmazonInboundTranslator.translateShipmentItemsPayload(payload));
            nextToken = payload.path("NextToken").asText(null);
            if (StrUtil.isBlank(nextToken)) {
                return itemsByShipment;
            }
        }
        throw new IllegalStateException("getShipmentItems 翻页超出防御上限(> " + MAX_PAGES + " 页),中止防 NextToken 异常自旋");
    }

    /** 签名(service=execute-api,时间戳取注入 Clock,固定时钟单测);POST/PUT body 参与签名载荷 */
    private SpApiSigner.SignedHeaders sign(String method, String path, Map<String, String> query,
                                           String body, SpApiSigner.AwsCredentials awsCredentials) {
        return signer.sign(new SpApiSigner.SpApiRequest(method, path, query,
                Map.of("Host", baseUri.getHost()), body), awsCredentials, region, SERVICE, Instant.now(clock));
    }

    /** 发请求并取 payload 节点(POST/PUT 带 body;GET 无);成功与失败响应都上报限流头(429 亦携带) */
    private JsonNode execute(String method, String path, String body, SpApiSigner.SignedHeaders signed,
                             String bucket, String operation, String lwaAccessToken,
                             SpApiSigner.AwsCredentials awsCredentials) {
        String url = baseUri + path + "?" + signed.canonicalQueryString();
        org.springframework.http.ResponseEntity<JsonNode> entity;
        try {
            RestClient.RequestHeadersSpec<?> spec;
            if ("GET".equals(method)) {
                spec = restClient.get().uri(URI.create(url));
            } else {
                spec = restClient.method(org.springframework.http.HttpMethod.valueOf(method))
                        .uri(URI.create(url))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);
            }
            spec = spec.header("Authorization", signed.authorization())
                    .header("X-Amz-Date", signed.xAmzDate())
                    .header("x-amz-access-token", lwaAccessToken);
            if (StrUtil.isNotBlank(awsCredentials.sessionToken())) {
                spec = spec.header("X-Amz-Security-Token", awsCredentials.sessionToken());
            }
            entity = spec.retrieve().toEntity(JsonNode.class);
        } catch (RestClientResponseException e) {
            reportRateLimit(bucket, operation, e.getResponseHeaders());
            // 响应原文可能含调用参数与账号上下文,只透出状态码定位问题(docs/07 §7)
            throw new IllegalStateException("SP-API " + operation + " 调用失败:HTTP " + e.getStatusCode().value(), e);
        }
        reportRateLimit(bucket, operation, entity.getHeaders());
        JsonNode responseBody = entity.getBody();
        if (responseBody == null || responseBody.get("payload") == null) {
            throw new IllegalStateException("SP-API " + operation + " 响应缺 payload");
        }
        return responseBody.get("payload");
    }

    /** 限流头上报(guard 缺位 observer=null 静默跳过;值解析与取舍归 PlatformRateGuard) */
    private void reportRateLimit(String bucket, String operation, HttpHeaders headers) {
        if (observer == null || headers == null) {
            return;
        }
        String value = headers.getFirst(PlatformRateGuard.RATE_LIMIT_HEADER);
        if (StrUtil.isNotBlank(value)) {
            observer.observe(bucket, operation, value);
        }
    }
}
