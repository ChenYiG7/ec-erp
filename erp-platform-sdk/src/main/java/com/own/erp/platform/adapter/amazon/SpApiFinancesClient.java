package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : SP-API Finances v0(2026-08-26)退款事件拉取客户端(#3 联调预备骨架):
 *         - 选型拍板(docs/04):Amazon 退款无独立状态流接口,退款事实以 Finances API listFinancialEvents
 *           的 RefundEventList 为准(PostedAfter/Before「记账时间」窗口 + NextToken 翻页),
 *           与订单拉单同「更新时间窗 + 游标左叠」模式;Listings/Orders 类状态接口不含退款金额;
 *         - 每页取 payload.FinancialEvents.RefundEventList(事件列表字段名取自官方 finances-api-model,
 *           真实报文联调时校准);其余事件列表(ShipmentEventList/ServiceFeeEventList 等)暂不消费
 *           ——退款金额对账属三期 settlement,扩容只加提取方法不改调用形态(docs/07 §8 演进规约);
 *         - 请求形态与 {@link SpApiOrdersClient} 同款:SpApiSigner 签名(service=execute-api),
 *           查询串直接用签名器回传 canonicalQueryString 禁二次拼参,LWA accessToken 走 x-amz-access-token 头;
 *         - base-url/region 可注入:单测 JDK HttpServer 假服务不出网(AIR,docs/07 §10);
 *           异常消息只带 HTTP 状态码,禁回显响应原文(docs/07 §7)
 */
public class SpApiFinancesClient {

    private static final String FINANCIAL_EVENTS_PATH = "/finances/2020-08-26/financialEvents";
    private static final String SERVICE = "execute-api";
    /** 翻页防御上限:NextToken 异常自旋时中止本窗口(拉单重试下轮),同 SpApiOrdersClient 口径 */
    private static final int MAX_PAGES = 50;

    private static final DateTimeFormatter AMZ_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final URI baseUri;
    private final String region;
    private final Clock clock;
    private final RestClient restClient;
    private final SpApiSigner signer = new SpApiSigner();

    public SpApiFinancesClient(String baseUrl, String region, Clock clock) {
        this.baseUri = URI.create(baseUrl);
        this.region = region;
        this.clock = clock;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 拉取时间窗内全部退款事件(内部翻页取全):窗口为「记账时间」(PostedAfter/PostedBefore),
     * 事件按条返回、不做聚合,聚合/翻译归调用方(AmazonRefundTranslator)。
     * listFinancialEvents 为账号级接口,无 marketplace 过滤参数
     */
    public List<JsonNode> pullRefundEvents(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials,
                                           Instant start, Instant end) {
        List<JsonNode> events = new ArrayList<>();
        String nextToken = null;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, String> query = new TreeMap<>();
            // SP-API 语义:带 NextToken 时其余过滤参数被忽略,只保留翻页令牌(同 getOrders)
            if (nextToken != null) {
                query.put("NextToken", nextToken);
            } else {
                query.put("PostedAfter", AMZ_ISO.format(start));
                query.put("PostedBefore", AMZ_ISO.format(end));
            }
            SpApiSigner.SignedHeaders signed = sign("GET", FINANCIAL_EVENTS_PATH, query, awsCredentials);
            JsonNode payload = execute(signed, lwaAccessToken, awsCredentials);
            for (JsonNode event : payload.path("FinancialEvents").path("RefundEventList")) {
                events.add(event);
            }
            nextToken = payload.path("NextToken").asText(null);
            if (StrUtil.isBlank(nextToken)) {
                return events;
            }
        }
        throw new IllegalStateException("listFinancialEvents 翻页超出防御上限(单窗口 > " + MAX_PAGES + " 页),中止防 NextToken 异常自旋");
    }

    /** 签名(service=execute-api,时间戳取注入 Clock,固定时钟单测);查询串取签名器回传的规范串拼 URL */
    private SpApiSigner.SignedHeaders sign(String method, String path, Map<String, String> query,
                                           SpApiSigner.AwsCredentials awsCredentials) {
        return signer.sign(new SpApiSigner.SpApiRequest(method, path, query, Map.of("Host", baseUri.getHost()), null),
                awsCredentials, region, SERVICE, Instant.now(clock));
    }

    /** 发请求并取 payload 节点(同 SpApiOrdersClient.execute:安全令牌头随临时凭证发出,两头须一致) */
    private JsonNode execute(SpApiSigner.SignedHeaders signed, String lwaAccessToken,
                             SpApiSigner.AwsCredentials awsCredentials) {
        String url = baseUri + FINANCIAL_EVENTS_PATH + "?" + signed.canonicalQueryString();
        JsonNode body;
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri(URI.create(url))
                    .header("Authorization", signed.authorization())
                    .header("X-Amz-Date", signed.xAmzDate())
                    .header("x-amz-access-token", lwaAccessToken);
            if (StrUtil.isNotBlank(awsCredentials.sessionToken())) {
                spec = spec.header("X-Amz-Security-Token", awsCredentials.sessionToken());
            }
            body = spec.retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            // 响应原文可能含调用参数与账号上下文,只透出状态码定位问题(docs/07 §7)
            throw new IllegalStateException("SP-API listFinancialEvents 调用失败:HTTP " + e.getStatusCode().value(), e);
        }
        if (body == null || body.get("payload") == null) {
            throw new IllegalStateException("SP-API listFinancialEvents 响应缺 payload");
        }
        return body.get("payload");
    }
}
