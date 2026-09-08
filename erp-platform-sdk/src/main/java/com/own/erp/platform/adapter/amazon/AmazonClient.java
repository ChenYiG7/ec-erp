package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.own.erp.platform.AuthToken;
import com.own.erp.platform.PlatformClient;
import com.own.erp.platform.PlatformShipment;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.ShopSession;
import com.own.erp.platform.unified.UnifiedOrder;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.platform.unified.UnifiedRefund;
import com.own.erp.platform.unified.UnifiedSettlement;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : Amazon SP-API adapter(#3,接入中,防腐层实现类):
 *         - 已落地:LWA 授权跳转地址、授权码换 Token、刷新 Token(LwaTokenClient);
 *           Orders 报文翻译(AmazonOrderTranslator,官方样例报文单测)
 *         - 已落地(2026-09-05):AWS SigV4 签名器(SpApiSigner,botocore 官方实现冻结时钟交叉验证)+
 *           STS AssumeRole 临时凭证客户端(StsTokenClient,XXE 防护)
 *         - 已落地(2026-09-05):SP-API getOrders/getOrderItems 拉单接线(SpApiOrdersClient,更新时间窗 +
 *           NextToken 翻页,假服务单测);AWS 密钥走环境变量/local.properties(键=环境变量名,禁入配置文件),
 *           配置 role-arn 时经 STS 换临时凭证并缓存、过期前 10 分钟刷新(与 LWA 刷新节奏同口径,docs/04);
 *           剩余真凭证联调(Seller Central 应用授权 + IAM 权限),SP-API 限流真值随实调按响应头校准
 *         - 已落地(2026-09-06 #3 联调预备骨架):pullProducts 选型 Reports GET_MERCHANT_LISTINGS_ALL_DATA
 *           (SpApiReportsClient 三步异步链 + AmazonListingTranslator TSV 翻译)/ pullRefunds 选型
 *           Finances listFinancialEvents(SpApiFinancesClient 窗口翻页 + AmazonRefundTranslator);
 *           翻译 fixture 为官方模板/schema 推导样例,真凭证样本到位后 --force 校准(docs/07 §8);
 *           uploadTracking 已脱机落地(2026-09-06,MFN confirmShipment + SPI PlatformShipment 签名收口,
 *           假服务单测;#11 ship 编排接线随联调)
 *         - 已落地(2026-09-04):OAuth 回调 + Token 刷新收口 erp-shop 授权中心(applyOAuthToken 复用加密链路,
 *           docs/04 授权中心)、按 shop 限流(PlatformGateway→PlatformRateGuard,配额窗口状态持久化 Redis)
 *         - 默认不注册 Bean:erp.adapter.amazon.enabled=true 才启用;未启用时拉单调度按"adapter 未接入"自动跳过
 *           (AdapterRegistry miss),不会产生 pull_log 失败噪音
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "erp.adapter.amazon.enabled", havingValue = "true")
public class AmazonClient implements PlatformClient {

    /** 北美站授权页;EU/JP 站点差异随 SigV4 实现一并配置化(#3) */
    private static final String AUTH_CONSENT_URL = "https://sellercentral.amazon.com/apps/authorize/consent";

    /** STS 临时凭证提前刷新窗口:过期前 10 分钟(与 LWA Token 刷新节奏同口径,docs/04 授权中心) */
    private static final Duration STS_REFRESH_AHEAD = Duration.ofMinutes(10);

    private final LwaTokenClient lwaTokenClient;
    private final SpApiOrdersClient spApiOrdersClient;
    private final SpApiFinancesClient spApiFinancesClient;
    private final SpApiReportsClient spApiReportsClient;
    private final StsTokenClient stsTokenClient;
    private final Clock clock;

    /** STS 临时会话缓存(调度单线程 OrderPullJob,volatile 足够;引入并发入口时再上锁,随实调评估) */
    private volatile StsTokenClient.StsSession stsSession;

    /** SP-API 应用 ID(Seller Central 创建应用后获得,参与授权跳转);未配置时授权地址不可用 */
    @Value("${erp.adapter.amazon.app-id:}")
    private String appId;

    /** AWS 长期 IAM 密钥(assume-role 发起方;走环境变量/local.properties,键=环境变量名,禁入配置文件) */
    @Value("${erp.adapter.amazon.aws-access-key:}")
    private String awsAccessKey;

    @Value("${erp.adapter.amazon.aws-secret-key:}")
    private String awsSecretKey;

    /** SP-API 访问角色 ARN(官方推荐最小权限临时凭证);未配置时直接用长期密钥签名 */
    @Value("${erp.adapter.amazon.role-arn:}")
    private String roleArn;

    @Value("${erp.adapter.amazon.role-session-name:erp-pull}")
    private String roleSessionName;

    @Value("${erp.adapter.amazon.sts-region:us-east-1}")
    private String stsRegion;

    @Override
    public PlatformType platform() {
        return PlatformType.AMAZON;
    }

    /**
     * LWA 授权跳转地址(Marketplace App Authorization 授权页):redirect_uri 在应用侧配置,
     * 这里只拼 application_id 与 state(回调原样带回,防 CSRF);未配置 appId 即抛业务异常提示配置
     */
    @Override
    public String buildAuthUrl(String redirectUri, String state) {
        if (StrUtil.isBlank(appId)) {
            throw new IllegalStateException("未配置 erp.adapter.amazon.app-id,无法生成 Amazon 授权地址");
        }
        return AUTH_CONSENT_URL + "?application_id=" + appId
                + "&state=" + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8)
                + "&version=beta";
    }

    /** LWA 授权码换 Token */
    @Override
    public AuthToken exchangeToken(String authCode, String redirectUri, String appKey, String appSecret) {
        return lwaTokenClient.exchangeToken(authCode, redirectUri, appKey, appSecret);
    }

    /** LWA 刷新 Token(LWA 的 refresh_token 不轮换,长期有效) */
    @Override
    public AuthToken refreshToken(String refreshToken, String appKey, String appSecret) {
        return lwaTokenClient.refreshToken(refreshToken, appKey, appSecret);
    }

    /**
     * SP-API getOrders 拉单(更新时间窗 + NextToken 翻页,详见 SpApiOrdersClient):
     * LWA accessToken 取自会话,当前有效 AWS 凭证见 {@link #currentAwsCredentials()};
     * 剩余真凭证联调(Seller Central 应用授权 + IAM 权限),假服务单测已覆盖请求形态
     */
    @Override
    public List<UnifiedOrder> pullOrders(ShopSession session, Instant start, Instant end) {
        if (session == null || session.getToken() == null
                || StrUtil.isBlank(session.getToken().getAccessToken())) {
            throw new IllegalStateException("ShopSession 缺 LWA accessToken,无法调用 SP-API");
        }
        return spApiOrdersClient.pullOrders(session.getToken().getAccessToken(), currentAwsCredentials(), start, end);
    }

    /**
     * 当前有效 AWS 凭证:未配置 role-arn 直接用长期 IAM 密钥;配置了经 STS AssumeRole 换临时凭证并缓存,
     * 过期前 10 分钟刷新(与 LWA 刷新节奏同口径,docs/04);刷新失败上抛 → 拉单记 pull_log 走连续失败告警
     */
    private SpApiSigner.AwsCredentials currentAwsCredentials() {
        if (StrUtil.isBlank(awsAccessKey) || StrUtil.isBlank(awsSecretKey)) {
            throw new IllegalStateException(
                    "未配置 AWS 密钥(erp.adapter.amazon.aws-access-key/aws-secret-key,走环境变量/local.properties)");
        }
        SpApiSigner.AwsCredentials base = new SpApiSigner.AwsCredentials(awsAccessKey, awsSecretKey, null);
        if (StrUtil.isBlank(roleArn)) {
            return base;
        }
        StsTokenClient.StsSession cached = stsSession;
        if (cached == null || Instant.now(clock).plus(STS_REFRESH_AHEAD).isAfter(cached.expiration())) {
            cached = stsTokenClient.assumeRole(base, roleArn, roleSessionName, stsRegion);
            stsSession = cached;
        }
        return cached.credentials();
    }

    /**
     * listing 同步(#3 联调预备骨架,2026-09-06 选型拍板:Reports 全量快照,docs/04):
     * GET_MERCHANT_LISTINGS_ALL_DATA 报表三步异步链(创建→轮询→下载)全走完再翻译落库,
     * start/end 时间窗不参与过滤(全量快照 + uk upsert 幂等,PRODUCT 游标仅作拉取频率控制)。
     * TODO(#3): 报表平台侧生成 15~60 分钟,轮询在拉单线程内同步等待,ProductPullJob 接真凭证后
     * 实测时长,必要时演进"一天一拉 + 异步任务化";Sku.currency 已按站点静态表推导
     * (AmazonMarketplace,2026-09-06 无凭证落地,真凭证到位后抽样核对)
     */
    @Override
    public List<UnifiedProduct> pullProducts(ShopSession session, Instant start, Instant end) {
        if (session == null || session.getToken() == null
                || StrUtil.isBlank(session.getToken().getAccessToken())) {
            throw new IllegalStateException("ShopSession 缺 LWA accessToken,无法调用 SP-API");
        }
        // 币种先于报表创建推导:未配置/未收录站点即报错,不空耗平台侧 15~60 分钟报表生成
        String currency = AmazonMarketplace.currencyOf(spApiReportsClient.marketplaceId());
        String accessToken = session.getToken().getAccessToken();
        SpApiSigner.AwsCredentials awsCredentials = currentAwsCredentials();
        String reportId = spApiReportsClient.requestListingReport(accessToken, awsCredentials);
        String documentId = spApiReportsClient.awaitListingReportDocument(accessToken, awsCredentials, reportId);
        String tsv = spApiReportsClient.fetchListingReportContent(accessToken, awsCredentials, documentId);
        return AmazonListingTranslator.translate(tsv, session.getShopId(), platform(), currency);
    }

    /**
     * 退款同步(#3 联调预备骨架,2026-09-06 选型拍板:Finances listFinancialEvents,docs/04):
     * PostedAfter/Before 记账时间窗 + NextToken 翻页拉 RefundEventList,逐条翻译
     * (FINISHED + REFUND_ONLY → #12 saveUnifiedRefund 分流 REFUNDED 终态回传);
     * 售后拉单 Job 接线随真凭证(接早了会对假报文产生 pull_log 失败噪音,#12 口径)
     */
    @Override
    public List<UnifiedRefund> pullRefunds(ShopSession session, Instant start, Instant end) {
        if (session == null || session.getToken() == null
                || StrUtil.isBlank(session.getToken().getAccessToken())) {
            throw new IllegalStateException("ShopSession 缺 LWA accessToken,无法调用 SP-API");
        }
        List<JsonNode> events = spApiFinancesClient.pullRefundEvents(
                session.getToken().getAccessToken(), currentAwsCredentials(), start, end);
        return events.stream()
                .map(event -> AmazonRefundTranslator.translateRefund(event, session.getShopId(), platform()))
                .toList();
    }

    /**
     * 结算报告同步(#19 财务域 2026-09-08 脱机落地,docs/04 拍板):
     * GET_V2_SETTLEMENT_REPORT_DATA_FLAT_FILE_V2 已生成报告搜索(V2 flat file 官方 2026-11-11 移除 V1,
     * 禁再引用)→ 逐份下载 → AmazonSettlementTranslator 翻译;无时间窗(离散正本非时序流,SPI 签名注释),
     * 重拉靠 settlement_report uk_shop_settlement 幂等 upsert 兜底(落库侧 erp-finance,编排接线随联调拍板);
     * 币种无需站点推导——V2 报文自带 currency 列(与 listing 报表差异);真凭证样本到位后 --force 校准一轮
     */
    @Override
    public List<UnifiedSettlement> pullSettlements(ShopSession session) {
        if (session == null || session.getToken() == null
                || StrUtil.isBlank(session.getToken().getAccessToken())) {
            throw new IllegalStateException("ShopSession 缺 LWA accessToken,无法调用 SP-API");
        }
        String accessToken = session.getToken().getAccessToken();
        SpApiSigner.AwsCredentials awsCredentials = currentAwsCredentials();
        List<UnifiedSettlement> settlements = new ArrayList<>();
        for (SpApiReportsClient.SettlementReportRef ref : spApiReportsClient.listSettlementReports(accessToken, awsCredentials)) {
            String tsv = spApiReportsClient.fetchSettlementReportContent(accessToken, awsCredentials, ref.documentId());
            settlements.add(AmazonSettlementTranslator.translate(tsv, session.getShopId(), platform()));
        }
        return settlements;
    }

    /**
     * MFN 自发货确认发货回传(2026-09-06 #3 脱机落地,docs/04 拍板):
     * POST /orders/v0/orders/{orderId}/shipment,行级发运 + 包裹物流详情,
     * 请求形态见 {@link SpApiOrdersClient#confirmShipment};
     * FBA/海外仓平台自履约不回传(编排侧裁剪不装配,docs/04 国内 vs 跨境差异表);
     * 时序拍板:#11 ship 本地推进后回传,回传成功才视为发货闭环,失败记 pull_log 告警但不回滚本地发货
     * (平台侧可手工补)——ship 编排接线随 #11 联调;真凭证联调时校准请求形态(docs/07 §8)
     */
    @Override
    public void uploadTracking(ShopSession session, PlatformShipment shipment) {
        if (session == null || session.getToken() == null
                || StrUtil.isBlank(session.getToken().getAccessToken())) {
            throw new IllegalStateException("ShopSession 缺 LWA accessToken,无法调用 SP-API");
        }
        spApiOrdersClient.confirmShipment(session.getToken().getAccessToken(), currentAwsCredentials(), shipment);
    }

    /** 跨境平台无电子面单取号(docs/04),恒不支持 */
    @Override
    public String fetchWaybill(ShopSession session, String platformOrderId) {
        throw new UnsupportedOperationException("Amazon 无电子面单取号(跨境直发走物流商)");
    }
}
