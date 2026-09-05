package com.own.erp.config;

import cn.hutool.core.util.StrUtil;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : Redisson 装配(#13 锁选型定版,docs/07 §1 ③):
 *     - 只用核心包 org.redisson:redisson,不引 redisson-spring-boot-starter——Boot 4 太新,
 *       starter autoconfig 的版本耦合不值得;连接参数手工读 spring.data.redis.*(与 Lettuce 同源单一真相)
 *     - @Lazy:客户端创建会真连 Redis,延迟到 LockService 首次抢锁才初始化——本地 Redis 未起时应用照常
 *       启动,锁侧按 erp.lock.fail-open 降级(2026-09-04 拍板:Redis 不绑架拉单业务)
 *     - 连接超时收紧(默认 connectTimeout 10s×重试 3 次会让每次降级阻塞数十秒):锁不可用要快速失败,
 *       降级才有意义
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        SingleServerConfig single = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(1)
                .setRetryInterval(500);
        if (StrUtil.isNotBlank(password)) {
            single.setPassword(password);
        }
        return Redisson.create(config);
    }
}
