package com.own.erp.common.api;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 系统参数变更事件(#18 系统设置):SystemConfigService.saveGroup 保存后发布,
 *         缓存持有方(erp-api SystemConfigApiImpl TTL 缓存)监听即时失效——读路径跨模块解耦
 *         (事件只进 erp-common,发布方 erp-system 与监听方 erp-api 互不依赖,铁律 2 同款思路)。
 *         携带组名与提交键集供监听方精准失效(空键集 = 全量失效,调用方自行兜底);
 *         事件是进程内 Spring 事件,多实例部署时其他实例靠 TTL(30s)自然过期,对运维参数可接受
 */
public record SystemConfigChangedEvent(String configGroup, java.util.Set<String> configKeys) {

    /** 全量失效便捷工厂(键集未知/批量维护场景) */
    public static SystemConfigChangedEvent all(String configGroup) {
        return new SystemConfigChangedEvent(configGroup, java.util.Set.of());
    }
}
