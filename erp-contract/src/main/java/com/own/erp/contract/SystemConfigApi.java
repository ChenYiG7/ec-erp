package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 系统参数读取契约(#18 系统设置,接口模块方案):业务域禁横向依赖 erp-system(铁律 2),
 *         实现收口 erp-api(SystemConfigApiImpl 委托 erp-system SystemConfigService)。
 *         语义:按参数键取 DB 覆盖值(文本原样),DB 无行/值为空返回 null——消费侧回落代码默认值;
 *         实现带轻量 TTL 缓存(保存端点主动失效),兜 Job 高频取值的 DB 压力。
 *         类型解析在消费侧(各域 runtime 配置类),解析失败回落默认值不抛错——配置错误不阻断业务
 */
public interface SystemConfigApi {

    /** 取参数覆盖值;DB 无行/空值返回 null(消费侧回落代码默认值) */
    String valueOf(String configKey);
}
