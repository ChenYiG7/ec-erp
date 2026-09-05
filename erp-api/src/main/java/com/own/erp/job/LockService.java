package com.own.erp.job;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 分布式互斥锁唯一入口(#13 锁选型定版,docs/07 §1 ③):效率锁的跨进程形态统一 Redisson
 *     per-key,禁散落手写 setnx / 进程内锁混用。当前使用方 = 拉单防重入;二期 Token 刷新 single-flight
 *     (轮换型 refresh_token 平台)同走本类,lock:token:{shopId} 与拉单 key 不互嵌,不会两套锁系统。
 *     降级语义(2026-09-04 拍板):Redis 故障不绑架拉单——erp.lock.fail-open 默认 true,一期单实例无第二
 *     进程,无锁照跑靠 uk 幂等兜底;二期多实例置 false(锁不可用宁停拉,不重复拉撞平台风控)。
 *     ⚠️ 本类只装互斥(防重入/single-flight),不装数据正确性——库存等共享 DB 行走原子 UPDATE(docs/07 §1 ①)
 */
@Slf4j
@Component
public class LockService {

    /** key 统一命名空间前缀,与 Redis 上缓存等其他用途隔离(docs/07 §1 常量归类) */
    private static final String KEY_PREFIX = "erp:lock:";

    private final ObjectProvider<RedissonClient> redissonProvider;
    /** Redis 故障降级开关:语义见类注释 */
    private final boolean failOpen;

    public LockService(ObjectProvider<RedissonClient> redissonProvider,
                       @Value("${erp.lock.fail-open:true}") boolean failOpen) {
        this.redissonProvider = redissonProvider;
        this.failOpen = failOpen;
    }

    /**
     * 非阻塞抢锁:锁被占返回 null,调用方跳过本轮,不排队。
     * 看门狗自动续期(不指定 leaseTime,默认 30s 每 10s 续),JVM 挂掉锁随 TTL 自愈——
     * 分钟级临界区(平台外呼)不会锁超时误释放,这是弃手写 setnx 的根因(docs/07 §1 ②)。
     *
     * @return 租约(finally 中 close() 释放);锁被占 或(Redis 故障且 fail-closed)= null;
     *         Redis 故障且 fail-open = 降级租约(close 无操作,业务照跑,已记 warn)
     */
    public Lease tryAcquire(String key) {
        try {
            RLock lock = redissonProvider.getObject().getLock(KEY_PREFIX + key);
            if (!lock.tryLock()) {
                return null;
            }
            return new RedissonLease(lock);
        } catch (RuntimeException e) {
            if (failOpen) {
                log.warn("分布式锁不可用,降级无锁执行(幂等由唯一键兜底) key={} :{}", key, e.getMessage());
                return new DegradedLease();
            }
            log.warn("分布式锁不可用,跳过本轮(erp.lock.fail-open=false) key={} :{}", key, e.getMessage());
            return null;
        }
    }

    /** 租约:close 即释放;实现不对外暴露(RLock 细节不出本类) */
    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    /** 真实租约:看门狗续期中,close 原子释放 */
    private static final class RedissonLease implements Lease {
        private final RLock lock;

        private RedissonLease(RLock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            if (!lock.isHeldByCurrentThread()) {
                return;
            }
            try {
                lock.unlock();
            } catch (RuntimeException e) {
                log.warn("分布式锁释放异常,将随看门狗 TTL 自愈 :{}", e.getMessage());
            }
        }
    }

    /** 降级租约:Redis 故障时的无锁通行证,close 无操作 */
    private static final class DegradedLease implements Lease {
        @Override
        public void close() {
            // 无锁可释
        }
    }
}
