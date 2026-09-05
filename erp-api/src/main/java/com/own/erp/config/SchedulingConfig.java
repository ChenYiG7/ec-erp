package com.own.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.time.ZoneId;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 拉单调度基础设施(#4):开关、调度线程池、统一时钟。
 *         单线程顺序拉取——fixedDelay 上一轮结束再计时,进程内天然防重入;
 *         跨进程防重入 = 店铺级 Redisson 锁 LockService(docs/07 §1 ③,2026-09-04 #13 定版一期即定型,
 *         Redis 故障按 erp.lock.fail-open 降级);二期多实例吞吐不足再议 worker 分片(分片管吞吐、锁管互斥兜底)
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** 拉单调度线程:命名前缀可追踪(docs/07 §1 并发要点);池大小 1 = 店铺顺序拉取,单店慢不并发放大平台限流压力 */
    @Bean
    public ThreadPoolTaskScheduler pullScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pull-sched-");
        return scheduler;
    }

    /**
     * 拉单窗口/游标统一时钟(AIR 可重复:单测注入固定 Clock,docs/07 §10);
     * 时区显式 Asia/Shanghai,与 datasource serverTimezone 同源,不随服务器漂移
     */
    @Bean
    public Clock pullClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
