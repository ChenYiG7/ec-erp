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

    /** 拉取类桶(pullOrders/pullProducts/pullRefunds) */
    public static final String BUCKET_PULL = "pull";
    /** 回写类桶(uploadTracking/fetchWaybill,随 #11 起用) */
    public static final String BUCKET_WRITE = "write";

    static final String KEY_PREFIX = "erp:ratelimit:";

    private static final String ENABLED_KEY = "erp.rate.enabled";
    private static final String FAIL_OPEN_KEY = "erp.rate.fail-open";
    private static final String DEFAULT_INTERVAL_KEY = "erp.rate.default-interval-ms";

    /** 默认 2 秒 1 次:未显式配置的平台/桶走保守值;SP-API 真值以响应头 x-amzn-RateLimit-Limit 为准(随 #3 实调校准) */
    private static final long DEFAULT_INTERVAL_MS = 2000L;
    /** 配额等待上限 = 间隔 + 余量:调度节奏(分钟级)远大于桶间隔,常态零等待;超时即真超限或 Redis 异常 */
    private static final long WAIT_SLACK_MS = 2000L;

    private final ObjectProvider<RedissonClient> redissonProvider;
    private final Environment environment;
    /** 已下发过 setRate 的 Redis key(每 JVM 一次,避免每次调用都写配置) */
    private final Set<String> configuredKeys = ConcurrentHashMap.newKeySet();

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

    /** 间隔 = erp.rate.{platform}.{bucket}-interval-ms,缺省回落 default-interval-ms(平台差异只进配置,不进代码) */
    private long resolveIntervalMs(PlatformType platform, String bucket) {
        String specific = "erp.rate." + platform.name().toLowerCase() + "." + bucket + "-interval-ms";
        return environment.getProperty(specific, Long.class,
                environment.getProperty(DEFAULT_INTERVAL_KEY, Long.class, DEFAULT_INTERVAL_MS));
    }

    private void ensureRate(RRateLimiter limiter, long intervalMs) {
        if (!configuredKeys.add(limiter.getName())) {
            return;
        }
        // OVERALL:该 key(=平台×桶×店铺)全局共享一个桶,二期多实例天然合并限流;平滑 1 次/间隔,无突发
        limiter.setRate(RateType.OVERALL, 1, intervalMs, RateIntervalUnit.MILLISECONDS);
    }
}
