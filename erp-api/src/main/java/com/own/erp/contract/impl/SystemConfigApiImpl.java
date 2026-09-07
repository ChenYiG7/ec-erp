package com.own.erp.contract.impl;

import com.own.erp.common.api.SystemConfigChangedEvent;
import com.own.erp.contract.SystemConfigApi;
import com.own.erp.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 系统参数读取契约实现(#18,接口模块方案):委托 erp-system SystemConfigService,
 *         契约注入一律 @Lazy 断构造环(docs/07 §2.2,CurrentUserApiImpl 同款)。
 *         TTL 缓存收口在此(跨域消费侧零感知):Job 每轮多节点取值共读一次 DB;
 *         保存侧经 SystemConfigChangedEvent(erp-common)监听即时失效——发布方 erp-system 与监听方
 *         erp-api 互不依赖,事件只进 erp-common;多实例部署其余实例靠 TTL 自然过期。
 *         凭证类键(api-key 等)禁入 sys_config(docs/07 §7 红线),本类无掩码分支——
 *         词表白名单天然拦截,凭证只走环境变量/local.properties
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SystemConfigApiImpl implements SystemConfigApi {

    /** 缓存 TTL(毫秒):保存端点即时失效,最长 30 秒陈旧窗口对运维参数可接受 */
    static final long CACHE_TTL_MS = 30_000L;

    private final @Lazy SystemConfigService systemConfigService;
    private final java.util.concurrent.ConcurrentHashMap<String, CachedValue> cache =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public String valueOf(String configKey) {
        long now = System.currentTimeMillis();
        CachedValue cached = cache.get(configKey);
        if (cached != null && now - cached.cachedAt < CACHE_TTL_MS) {
            return cached.value;
        }
        String value = systemConfigService.valueOf(configKey);
        cache.put(configKey, new CachedValue(value, now));
        return value;
    }

    /** 保存动作即时失效缓存:精准按提交键清,兜底全量清(键集为空时);失效本身无副作用 */
    @EventListener
    public void onConfigChanged(SystemConfigChangedEvent event) {
        if (event.configKeys() == null || event.configKeys().isEmpty()) {
            cache.clear();
            log.info("系统参数变更,配置缓存全量失效:组 {}", event.configGroup());
            return;
        }
        event.configKeys().forEach(cache::remove);
        log.info("系统参数变更,配置缓存失效 {} 键:组 {}", event.configKeys().size(), event.configGroup());
    }

    /** 缓存条目(值 + 写入时刻;null 值也缓存,防「无覆盖行」键反复穿透);包级可见供单测构造陈旧条目 */
    record CachedValue(String value, long cachedAt) {
    }
}
