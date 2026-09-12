package com.own.erp.system.event;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 操作审计已采集事件(#27②):OperLogAspect 环绕 @OperLog 方法采集后发布,
 *         SysOperLogService 以 AFTER_COMMIT + fallbackExecution 相位消费落 sys_oper_log
 *         (同 WebhookPushService/NotifyPushedEvent 拍板:业务提交后才写审计,审计失败不回滚业务)。
 *         事件发布方与监听方同在 erp-system,不进 erp-common(无跨模块依赖,同 NotifyPushedEvent 先例)
 */
public record SysOperLogEvent(

        /** 操作人ID(未登录链路为 null) */
        Long userId,

        /** 操作人登录名快照 */
        String username,

        /** 业务模块 */
        String module,

        /** 动作标识 */
        String action,

        /** 关联业务类型 */
        String bizType,

        /** 关联业务主键 */
        Long bizId,

        /** 方法参数 JSON(已截断脱敏) */
        String paramsJson,

        /** 结果:OK/FAIL */
        String resultStatus,

        /** 异常摘要(FAIL 时) */
        String errorMsg,

        /** 操作人 IP */
        String ip,

        /** 链路ID */
        String traceId,

        /** 耗时毫秒 */
        Integer costMs
) {
}
