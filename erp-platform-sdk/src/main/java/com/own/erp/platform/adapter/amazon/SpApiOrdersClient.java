package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.unified.UnifiedOrder;
import org.springframework.http.MediaType;
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
 * @Date : 2026/9/5
 * @Description : SP-API Orders v0 拉单客户端(#3,getOrders + getOrderItems):
 *         - 按 LastUpdatedAfter/Before「更新时间」窗口拉取(docs/04 拉单策略 1,按下单时间会丢状态回传),
 *           NextToken 翻页(MaxCount ≤100/页),翻到无 NextToken 为止;
 *           逐单调 getOrderItems 挂明细后经 {@link AmazonOrderTranslator} 翻译成 UnifiedOrder
 *         - 请求经 {@link SpApiSigner} 签名(service=execute-api);查询串直接用签名器回传的
 *           canonicalQueryString(与签名严格一致,禁二次拼参,StsTokenClient 同款纪律);
 *           LWA accessToken 走 x-amz-access-token 头,不参与 SigV4(SigV4 仅签 host/日期/security-token)
 *         - base-url/region 可注入:单测 JDK HttpServer 假服务不出网(AIR,docs/07 §10),真凭证到位后只换配置
 *         - 异常消息只带 HTTP 状态码,禁回显响应原文(docs/07 §7)
 *         - 已知边界(真凭证联调时校准):getOrderItems 属页内循环调用,未单独走 PlatformRateGuard
 *           (横切统一施加在 pull 入口,docs/04),窗口单量大时是否需要页内 pacing 随 SP-API 真值评估
 *         - 回写(2026-09-06 #3 脱机落地):MFN 确认发货 POST /orders/v0/orders/{orderId}/shipment
 *           (confirmShipment,行级发运 + 包裹详情,shipDate 必填;成功 = 2xx 无响应体);
 *           请求体经 Jackson 构建(运单号/承运商名等外部值,禁手工拼接防注入),真凭证联调时校准请求形态
 */
public class SpApiOrdersClient {

    /** Jackson 线程安全,静态共享;仅用树模型不配特性 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ORDERS_PATH = "/orders/v0/orders";
    private static final String ORDER_ITEMS_PATH = "/orders/v0/orders/";
    private static final String SERVICE = "execute-api";
    /** 单页上限:getOrders MaxCount 合法区间 1~100(docs/04 拉单策略 1) */
    private static final String MAX_COUNT = "100";
    /** 翻页防御上限:NextToken 异常自旋时中止本窗口(拉单重试下轮),防死循环拖死调度线程 */
    private static final int MAX_PAGES = 50;

    private static final DateTimeFormatter AMZ_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final URI baseUri;
    private final String region;
    private final String marketplaceIds;
    private final Clock clock;
    private final RestClient restClient;
    private final SpApiSigner signer = new SpApiSigner();

    public SpApiOrdersClient(String baseUrl, String region, String marketplaceIds, Clock clock) {
        this.baseUri = URI.create(baseUrl);
        this.region = region;
        this.marketplaceIds = marketplaceIds;
        this.clock = clock;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 拉取时间窗内全部订单(内部翻页取全):lwaAccessToken 取自 ShopSession(授权中心装配),
     * awsCredentials 由调用方供给(长期 IAM 密钥或 STS 临时凭证,刷新归调用方)
     */
    public List<UnifiedOrder> pullOrders(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials,
                                         Instant start, Instant end) {
        if (StrUtil.isBlank(marketplaceIds)) {
            throw new IllegalStateException("未配置 erp.adapter.amazon.marketplace-ids,无法确定拉取站点");
        }
        List<UnifiedOrder> result = new ArrayList<>();
        String nextToken = null;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, String> query = new TreeMap<>();
            query.put("MarketplaceIds", marketplaceIds);
            // SP-API 语义:带 NextToken 时其余过滤参数被忽略,只保留翻页令牌
            if (nextToken != null) {
                query.put("NextToken", nextToken);
            } else {
                query.put("LastUpdatedAfter", AMZ_ISO.format(start));
                query.put("LastUpdatedBefore", AMZ_ISO.format(end));
                query.put("MaxCount", MAX_COUNT);
            }
            SpApiSigner.SignedHeaders signed = sign("GET", ORDERS_PATH, query, awsCredentials);
            JsonNode payload = execute(signed, lwaAccessToken, awsCredentials, "getOrders", ORDERS_PATH);
            for (JsonNode orderNode : payload.path("Orders")) {
                UnifiedOrder order = AmazonOrderTranslator.translateOrder(orderNode);
                order.setItems(fetchItems(order.getPlatformOrderId(), lwaAccessToken, awsCredentials));
                result.add(order);
            }
            nextToken = payload.path("NextToken").asText(null);
            if (StrUtil.isBlank(nextToken)) {
                return result;
            }
        }
        throw new IllegalStateException("getOrders 翻页超出防御上限(单窗口 > " + MAX_PAGES + " 页),中止防 NextToken 异常自旋");
    }

    /** 单订单明细(含 NextToken 翻页);getOrderItems 无日期过滤参数,仅令牌翻页 */
    private List<UnifiedOrder.Item> fetchItems(String platformOrderId, String lwaAccessToken,
                                               SpApiSigner.AwsCredentials awsCredentials) {
        String path = ORDER_ITEMS_PATH + platformOrderId + "/orderItems";
        List<UnifiedOrder.Item> items = new ArrayList<>();
        String nextToken = null;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Map<String, String> query = new TreeMap<>();
            query.put("MarketplaceIds", marketplaceIds);
            if (nextToken != null) {
                query.put("NextToken", nextToken);
            }
            SpApiSigner.SignedHeaders signed = sign("GET", path, query, awsCredentials);
            JsonNode payload = execute(signed, lwaAccessToken, awsCredentials, "getOrderItems", path);
            items.addAll(AmazonOrderTranslator.translateItems(payload.path("OrderItems")));
            nextToken = payload.path("NextToken").asText(null);
            if (StrUtil.isBlank(nextToken)) {
                return items;
            }
        }
        throw new IllegalStateException("getOrderItems 翻页超出防御上限 order=" + platformOrderId);
    }

    /**
     * MFN 自发货确认发货(POST /orders/v0/orders/{orderId}/shipment,#3 2026-09-06 脱机落地,
     * #11 ship 后由 erp-api 编排接线):行级发运(platformOrderItemId+quantity)+ 包裹详情
     * (运单号/承运商 code 或 name 兜底/shipDate 必填);成功 = 2xx(Amazon 返回 204 无响应体);
     * 入参校验先于网络调用,非法要素友好报错禁半配置出请求
     */
    public void confirmShipment(String lwaAccessToken, SpApiSigner.AwsCredentials awsCredentials,
                                PlatformShipment shipment) {
        if (StrUtil.isBlank(marketplaceIds)) {
            throw new IllegalStateException("未配置 erp.adapter.amazon.marketplace-ids,无法回传发货");
        }
        validateShipment(shipment);
        String path = "/orders/v0/orders/" + shipment.platformOrderId() + "/shipment";
        String body = buildShipmentBody(shipment);
        // POST body 参与签名载荷(Reports 客户端同款);查询串取签名器回传规范串,禁二次拼参
        SpApiSigner.SignedHeaders signed = signer.sign(new SpApiSigner.SpApiRequest(
                "POST", path, new TreeMap<>(), Map.of("Host", baseUri.getHost()), body),
                awsCredentials, region, SERVICE, Instant.now(clock));
        String url = baseUri + path + "?" + signed.canonicalQueryString();
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .header("Authorization", signed.authorization())
                    .header("X-Amz-Date", signed.xAmzDate())
                    .header("x-amz-access-token", lwaAccessToken);
            if (StrUtil.isNotBlank(awsCredentials.sessionToken())) {
                spec = spec.header("X-Amz-Security-Token", awsCredentials.sessionToken());
            }
            spec.retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            // 响应原文可能含调用参数与账号上下文,只透出状态码定位问题(docs/07 §7)
            throw new IllegalStateException("SP-API confirmShipment 调用失败:HTTP " + e.getStatusCode().value(), e);
        }
    }

    /** 回传要素校验:平台单号/运单号/发货时间/承运商(编码与名称至少其一)/行级明细(行 ID 非空+数量正数) */
    private void validateShipment(PlatformShipment shipment) {
        if (shipment == null || StrUtil.isBlank(shipment.platformOrderId())) {
            throw new IllegalStateException("发货回传缺 platformOrderId");
        }
        if (StrUtil.isBlank(shipment.trackingNo())) {
            throw new IllegalStateException("发货回传缺运单号 trackingNo");
        }
        if (shipment.shipTime() == null) {
            throw new IllegalStateException("发货回传缺发货时间 shipTime");
        }
        if (StrUtil.isBlank(shipment.carrierCode()) && StrUtil.isBlank(shipment.carrierName())) {
            throw new IllegalStateException("发货回传缺物流公司(carrierCode/carrierName 至少其一)");
        }
        if (shipment.items() == null || shipment.items().isEmpty()) {
            throw new IllegalStateException("发货回传缺行级发运明细 items");
        }
        for (PlatformShipment.Item item : shipment.items()) {
            if (item == null || StrUtil.isBlank(item.platformOrderItemId()) || item.quantity() <= 0) {
                throw new IllegalStateException("发货回传行级明细不合法(缺 platformOrderItemId 或数量非正数)");
            }
        }
    }

    /**
     * 确认发货请求体(marketplaceId + shipment.orderItems 行级 + packageDetails 包裹详情);
     * Jackson 树模型构建——运单号/承运商名是外部值,禁手工字符串拼接(docs/07 §7 注入防护)
     */
    private String buildShipmentBody(PlatformShipment shipment) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("marketplaceId", marketplaceIds);
        ObjectNode shipmentNode = root.putObject("shipment");
        ArrayNode orderItems = shipmentNode.putArray("orderItems");
        for (PlatformShipment.Item item : shipment.items()) {
            ObjectNode node = orderItems.addObject();
            node.put("orderItemId", item.platformOrderItemId());
            node.put("quantity", item.quantity());
        }
        ObjectNode packageDetails = shipmentNode.putObject("packageDetails");
        packageDetails.put("trackingNumber", shipment.trackingNo());
        if (StrUtil.isNotBlank(shipment.carrierCode())) {
            packageDetails.put("carrierCode", shipment.carrierCode());
        }
        if (StrUtil.isNotBlank(shipment.carrierName())) {
            packageDetails.put("carrierName", shipment.carrierName());
        }
        packageDetails.put("shipDate", AMZ_ISO.format(shipment.shipTime()));
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("发货回传请求体序列化失败", e);
        }
    }

    /** 签名(service=execute-api,时间戳取注入 Clock,固定时钟单测);查询串取签名器回传的规范串拼 URL */
    private SpApiSigner.SignedHeaders sign(String method, String path, Map<String, String> query,
                                           SpApiSigner.AwsCredentials awsCredentials) {
        return signer.sign(new SpApiSigner.SpApiRequest(method, path, query, Map.of("Host", baseUri.getHost()), null),
                awsCredentials, region, SERVICE, Instant.now(clock));
    }

    /** 发请求并取 payload 节点;security-token 临时凭证时随请求头发出(签名器已并入规范头,两头须一致) */
    private JsonNode execute(SpApiSigner.SignedHeaders signed, String lwaAccessToken,
                             SpApiSigner.AwsCredentials awsCredentials, String operation, String path) {
        String url = baseUri + path + "?" + signed.canonicalQueryString();
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
            throw new IllegalStateException("SP-API " + operation + " 调用失败:HTTP " + e.getStatusCode().value(), e);
        }
        if (body == null || body.get("payload") == null) {
            throw new IllegalStateException("SP-API " + operation + " 响应缺 payload");
        }
        return body.get("payload");
    }
}
