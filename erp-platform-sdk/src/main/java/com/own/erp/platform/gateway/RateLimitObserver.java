package com.own.erp.platform.gateway;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 平台限流响应头观测回调(函数式,#3 限流真值校准通道,docs/04):
 *     SP-API 各 endpoint 响应携带 x-amzn-RateLimit-Limit(该 seller 在该 endpoint 的每秒速率真值),
 *     HTTP 客户端层(SpApi*Client)成功与失败(429 亦携带)响应都原样上报,经装配点
 *     (AmazonAdapterConfig)绑定到 {@link PlatformRateGuard} 动态收紧对应桶间隔——
 *     横切不散落:客户端对限流器零感知只管上报;guard 不在容器时装配点传 null,客户端静默跳过。
 */
@FunctionalInterface
public interface RateLimitObserver {

    /** 拉取类桶(值与 PlatformRateGuard.BUCKET_PULL 同源,禁改不一致) */
    String BUCKET_PULL = PlatformRateGuard.BUCKET_PULL;
    /** 回写类桶(值与 PlatformRateGuard.BUCKET_WRITE 同源) */
    String BUCKET_WRITE = PlatformRateGuard.BUCKET_WRITE;

    /**
     * @param bucket      桶类型(BUCKET_PULL / BUCKET_WRITE)
     * @param operation   操作名(排障定位,如 getOrders / confirmShipment)
     * @param headerValue 响应头原值(每秒速率,如 "0.0083");解析与取舍归 PlatformRateGuard
     */
    void observe(String bucket, String operation, String headerValue);
}
