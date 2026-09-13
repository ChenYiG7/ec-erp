package com.own.erp.platform.adapter.amazon;

import com.own.erp.platform.PlatformType;
import com.own.erp.platform.gateway.PlatformRateGuard;
import com.own.erp.platform.gateway.RateLimitObserver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : Amazon adapter 装配:供 AmazonClient 注入的客户端 Bean(端点可配,单测/联调可指向本地假服务)。
 *         依赖容器内存在 java.time.Clock Bean(运行时由 erp-api SchedulingConfig 提供,
 *         erp-worker 迁移时需自带同款配置);本类不绑 spring-boot-autoconfigure 特性,普通 @Configuration 即可。
 *         限流真值校准(#3,2026-09-12):各 SpApi*Client 经 {@link RateLimitObserver} 上报
 *         x-amzn-RateLimit-Limit 响应头,guard 缺位时传 null(客户端静默跳过)——观测回调在此装配点
 *         绑定平台,客户端对限流器零感知(横切不散落)
 */
@Configuration
public class AmazonAdapterConfig {

    @Bean
    public LwaTokenClient lwaTokenClient(
            @Value("${erp.adapter.amazon.lwa-base-url:https://api.amazon.com}") String lwaBaseUrl,
            Clock clock) {
        return new LwaTokenClient(lwaBaseUrl, clock);
    }

    @Bean
    public SpApiOrdersClient spApiOrdersClient(
            @Value("${erp.adapter.amazon.spapi-base-url:https://sellingpartnerapi-na.amazon.com}") String baseUrl,
            @Value("${erp.adapter.amazon.spapi-region:us-east-1}") String region,
            @Value("${erp.adapter.amazon.marketplace-ids:}") String marketplaceIds,
            Clock clock,
            ObjectProvider<PlatformRateGuard> rateGuardProvider) {
        return new SpApiOrdersClient(baseUrl, region, marketplaceIds, clock, observerOf(rateGuardProvider));
    }

    /** Finances 客户端(退款事件,账号级接口无 marketplace 参数,#3 联调预备骨架) */
    @Bean
    public SpApiFinancesClient spApiFinancesClient(
            @Value("${erp.adapter.amazon.spapi-base-url:https://sellingpartnerapi-na.amazon.com}") String baseUrl,
            @Value("${erp.adapter.amazon.spapi-region:us-east-1}") String region,
            Clock clock,
            ObjectProvider<PlatformRateGuard> rateGuardProvider) {
        return new SpApiFinancesClient(baseUrl, region, clock, observerOf(rateGuardProvider));
    }

    /** Reports 客户端(listing 全量快照;轮询间隔默认 30s,平台侧报表生成约 15~60 分钟,#3 联调预备骨架) */
    @Bean
    public SpApiReportsClient spApiReportsClient(
            @Value("${erp.adapter.amazon.spapi-base-url:https://sellingpartnerapi-na.amazon.com}") String baseUrl,
            @Value("${erp.adapter.amazon.spapi-region:us-east-1}") String region,
            @Value("${erp.adapter.amazon.marketplace-ids:}") String marketplaceIds,
            Clock clock,
            @Value("${erp.adapter.amazon.report-poll-interval-ms:30000}") long reportPollIntervalMs,
            ObjectProvider<PlatformRateGuard> rateGuardProvider) {
        return new SpApiReportsClient(baseUrl, region, marketplaceIds, clock,
                java.time.Duration.ofMillis(reportPollIntervalMs), observerOf(rateGuardProvider));
    }

    /** Inbound 客户端(#35 fba-shipment V2:入库计划/板箱回传/收货状态拉取,联调随 #3 真凭证) */
    @Bean
    public SpApiInboundClient spApiInboundClient(
            @Value("${erp.adapter.amazon.spapi-base-url:https://sellingpartnerapi-na.amazon.com}") String baseUrl,
            @Value("${erp.adapter.amazon.spapi-region:us-east-1}") String region,
            @Value("${erp.adapter.amazon.marketplace-ids:}") String marketplaceIds,
            Clock clock,
            ObjectProvider<PlatformRateGuard> rateGuardProvider) {
        return new SpApiInboundClient(baseUrl, region, marketplaceIds, clock, observerOf(rateGuardProvider));
    }

    @Bean
    public StsTokenClient stsTokenClient(
            @Value("${erp.adapter.amazon.sts-base-url:https://sts.amazonaws.com}") String baseUrl,
            Clock clock) {
        return new StsTokenClient(baseUrl, clock);
    }

    /** 观测回调绑定 Amazon 平台(guard 缺位返 null,客户端侧静默跳过上报) */
    private static RateLimitObserver observerOf(ObjectProvider<PlatformRateGuard> rateGuardProvider) {
        PlatformRateGuard guard = rateGuardProvider.getIfAvailable();
        return guard == null ? null : guard.observer(PlatformType.AMAZON);
    }
}
