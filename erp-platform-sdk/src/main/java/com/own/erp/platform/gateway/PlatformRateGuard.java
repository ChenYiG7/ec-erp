package com.own.erp.platform.gateway;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.platform.PlatformType;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 平台 API 调用限流守卫(#3,docs/04 PlatformGateway 横切层):按 platform + shop + bucket
 *         三维令牌桶,底层 Redisson RRateLimiter——配额窗口状态持久化 Redis,重启/多实例不丢不重
 *         (docs/04 拉单策略 4:亚马逊每分钟调用预算有限,防重启丢配额状态;wimoor t_amz_api_timelimit 精神)。
 *         - 降级语义:Redis 故障按 erp.rate.fail-open 放行(限流是保护性横切,不绑架拉单,与 LockService 同哲学);
 *           但"配额等待超时"属真实超限,fail-closed 上抛 429,由调用方(拉单 Job)按失败重试;
 *         - 容器无 RedissonClient Bean(如无 Redis 运行形态)时整体直通;
 *         - setRate 每 JVM 每 key 只下发一次(Redis 侧令牌存量跨重启留存;改 yml 频率配置后需随应用重启生效)
 */
@Slf4j
@Component
public class PlatformRateGuard {

    /** 拉取类桶(pullOrders/pullProducts/pullRefunds/pullInboundShipments) */
    public static final String BUCKET_PULL = "pull";
    /** 回写类桶(uploadTracking/fetchWaybill/入库计划与板箱回传) */
    public static final String BUCKET_WRITE = "write";

    static final String KEY_PREFIX = "erp:ratelimit:";

    /** SP-API 限流响应头(#3 限流真值校准):值 = 该 seller 在该 endpoint 的每秒速率,如 "0.0083" */
    public static final String RATE_LIMIT_HEADER = "x-amzn-RateLimit-Limit";

    private static final String ENABLED_KEY = "erp.rate.enabled";
    private static final String FAIL_OPEN_KEY = "erp.rate.fail-open";
    private static final String DEFAULT_INTERVAL_KEY = "erp.rate.default-interval-ms";

    /** 默认 2 秒 1 次:未显式配置的平台/桶走保守值;真值随响应头 x-amzn-RateLimit-Limit 动态校准 */
    private static final long DEFAULT_INTERVAL_MS = 2000L;
    /** 配额等待上限 = 间隔 + 余量:调度节奏(分钟级)远大于桶间隔,常态零等待;超时即真超限或 Redis 异常 */
    private static final long WAIT_SLACK_MS = 2000L;
    /** 校准间隔封顶:响应头异常小值(limit→0)时不让间隔拉爆调度(120s 已低于 SP-API 已知最低配额节奏) */
    private static final long CALIBRATED_INTERVAL_CAP_MS = 120_000L;

    private final ObjectProvider<RedissonClient> redissonProvider;
    private final Environment environment;
    /** 已下发过 setRate 的 Redis key(每 JVM 一次,避免每次调用都写配置;校准刷新后按前缀失效重下发) */
    private final Set<String> configuredKeys = ConcurrentHashMap.newKeySet();
    /** 观测真值(每秒速率,多 endpoint 保守合并取最小):key = PLATFORM:bucket */
    private final Map<String, Double> observedMinLimit = new ConcurrentHashMap<>();

    public PlatformRateGuard(ObjectProvider<RedissonClient> redissonProvider, Environment environment) {
        this.redissonProvider = redissonProvider;
        this.environment = environment;
    }

    /**
     * 取一个调用许可:无可用许可时阻塞等待至超时,仍无则抛 429(真实超限,宁停拉不撞平台风控);
     * Redis 故障按 fail-open 配置降级放行或上抛
     */
    public void acquire(PlatformType platform, Long shopId, String bucket) {
        if (!environment.getProperty(ENABLED_KEY, Boolean.class, Boolean.TRUE)) {
            return;
        }
        try {
            RedissonClient redisson = redissonProvider.getIfAvailable();
            if (redisson == null) {
                return;
            }
            long intervalMs = resolveIntervalMs(platform, bucket);
            RRateLimiter limiter = redisson.getRateLimiter(KEY_PREFIX + platform.name().toLowerCase()
                    + ":" + bucket + ":" + shopId);
            ensureRate(limiter, intervalMs);
            if (!limiter.tryAcquire(intervalMs + WAIT_SLACK_MS, TimeUnit.MILLISECONDS)) {
                throw new BusinessException(429, "平台调用限流等待超时,platform=" + platform
                        + " shopId=" + shopId + " bucket=" + bucket);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            if (environment.getProperty(FAIL_OPEN_KEY, Boolean.class, Boolean.TRUE)) {
                log.warn("平台限流器不可用,降级放行(erp.rate.fail-open=true) platform={} shopId={} :{}",
                        platform, shopId, e.getMessage());
                return;
            }
            throw new BusinessException(503, "平台限流器不可用(erp.rate.fail-open=false),platform="
                    + platform + " shopId=" + shopId);
        }
    }

    /**
     * 间隔 = max(配置值, 观测真值):配置 = erp.rate.{platform}.{bucket}-interval-ms(缺省回落
     * default-interval-ms);观测真值 = 响应头 x-amzn-RateLimit-Limit 换算(ceil(1000/limit)),
     * 多 endpoint 保守合并取最小速率——两者取更保守者,宁慢不撞平台风控(docs/04)
     */
    private long resolveIntervalMs(PlatformType platform, String bucket) {
        String specific = "erp.rate." + platform.name().toLowerCase() + "." + bucket + "-interval-ms";
        long configured = environment.getProperty(specific, Long.class,
                environment.getProperty(DEFAULT_INTERVAL_KEY, Long.class, DEFAULT_INTERVAL_MS));
        Double observedLimit = observedMinLimit.get(platform.name() + ":" + bucket);
        if (observedLimit == null) {
            return configured;
        }
        long observed = Math.min((long) Math.ceil(1000.0 / observedLimit), CALIBRATED_INTERVAL_CAP_MS);
        return Math.max(configured, observed);
    }

    /**
     * 观测回调入口(装配点经 {@link #observer(PlatformType)} 绑定平台后交 SpApi*Client 上报):
     * 解析限流头收紧 (platform, bucket) 间隔;缺头/解析失败/非法值静默忽略——校准是增强,不绑架调用
     */
    public void observeRateLimit(PlatformType platform, String bucket, String operation, String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return;
        }
        double limitPerSecond;
        try {
            limitPerSecond = Double.parseDouble(headerValue.trim());
        } catch (NumberFormatException e) {
            log.debug("限流响应头解析失败,忽略 platform={} bucket={} operation={} value={}",
                    platform, bucket, operation, headerValue);
            return;
        }
        if (limitPerSecond <= 0 || limitPerSecond > 1000) {
            log.debug("限流响应头值非法,忽略 platform={} bucket={} operation={} value={}",
                    platform, bucket, operation, headerValue);
            return;
        }
        String key = platform.name() + ":" + bucket;
        boolean[] updated = {false};
        observedMinLimit.compute(key, (k, prev) -> {
            if (prev == null || limitPerSecond < prev) {
                updated[0] = true;
                return limitPerSecond;
            }
            return prev;
        });
        if (updated[0]) {
            // 该桶已下发的 setRate 配置全部失效,下次 acquire 用新间隔重新下发(前缀匹配跨店铺桶)
            String prefix = KEY_PREFIX + platform.name().toLowerCase() + ":" + bucket + ":";
            configuredKeys.removeIf(name -> name.startsWith(prefix));
            log.info("限流真值校准生效 platform={} bucket={} operation={} limitPerSecond={} intervalMs={}",
                    platform, bucket, operation, limitPerSecond,
                    resolveIntervalMs(platform, bucket));
        }
    }

    /** 生成绑定到指定平台的观测回调(装配点注入 SpApi*Client;guard 缺位时装配点传 null) */
    public RateLimitObserver observer(PlatformType platform) {
        return (bucket, operation, headerValue) -> observeRateLimit(platform, bucket, operation, headerValue);
    }

    private void ensureRate(RRateLimiter limiter, long intervalMs) {
        if (!configuredKeys.add(limiter.getName())) {
            return;
        }
        // OVERALL:该 key(=平台×桶×店铺)全局共享一个桶,二期多实例天然合并限流;平滑 1 次/间隔,无突发
        limiter.setRate(RateType.OVERALL, 1, intervalMs, RateIntervalUnit.MILLISECONDS);
    }
}
