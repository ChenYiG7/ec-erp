package com.own.erp.platform.adapter.douyin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店 adapter 装配:供 DouyinClient 注入的客户端 Bean(端点/凭证可配,单测/联调可指向本地假服务)。
 *         依赖容器内 java.time.Clock Bean(与 amazon 同源,docs/07 §10);普通 @Configuration,不绑 autoconfigure 特性。
 *         限流:抖店无限流响应头(跨境 x-amzn 先例不适用),真值校准走 erp.rate.douyin.* 配置(PlatformRateGuard),本装配点不绑观测
 */
@Configuration
public class DouyinAdapterConfig {

    @Bean
    public DouyinApiSupport douyinApiSupport(
            @Value("${erp.adapter.douyin.base-url:https://openapi-fxg.jinritemai.com}") String baseUrl,
            @Value("${erp.adapter.douyin.app-key:}") String appKey,
            @Value("${erp.adapter.douyin.app-secret:}") String appSecret,
            @Value("${erp.adapter.douyin.v:2}") String v,
            @Value("${erp.adapter.douyin.sign-method:hmac-sha256}") String signMethod,
            Clock clock) {
        return new DouyinApiSupport(baseUrl, appKey, appSecret, v, signMethod, clock);
    }

    @Bean
    public DouyinTokenClient douyinTokenClient(
            @Value("${erp.adapter.douyin.base-url:https://openapi-fxg.jinritemai.com}") String baseUrl,
            @Value("${erp.adapter.douyin.v:2}") String v,
            @Value("${erp.adapter.douyin.sign-method:hmac-sha256}") String signMethod,
            Clock clock) {
        return new DouyinTokenClient(baseUrl, v, signMethod, clock);
    }

    @Bean
    public DouyinOrdersClient douyinOrdersClient(DouyinApiSupport support) {
        return new DouyinOrdersClient(support);
    }

    @Bean
    public DouyinProductClient douyinProductClient(DouyinApiSupport support) {
        return new DouyinProductClient(support);
    }

    @Bean
    public DouyinRefundsClient douyinRefundsClient(DouyinApiSupport support) {
        return new DouyinRefundsClient(support);
    }

    @Bean
    public DouyinLogisticsClient douyinLogisticsClient(DouyinApiSupport support) {
        return new DouyinLogisticsClient(support);
    }
}