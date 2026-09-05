package com.own.erp.platform.adapter.amazon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : Amazon adapter 装配:供 AmazonClient 注入的客户端 Bean(端点可配,单测/联调可指向本地假服务)。
 *         依赖容器内存在 java.time.Clock Bean(运行时由 erp-api SchedulingConfig 提供,
 *         erp-worker 迁移时需自带同款配置);本类不绑 spring-boot-autoconfigure 特性,普通 @Configuration 即可
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
            Clock clock) {
        return new SpApiOrdersClient(baseUrl, region, marketplaceIds, clock);
    }

    @Bean
    public StsTokenClient stsTokenClient(
            @Value("${erp.adapter.amazon.sts-base-url:https://sts.amazonaws.com}") String baseUrl,
            Clock clock) {
        return new StsTokenClient(baseUrl, clock);
    }
}
