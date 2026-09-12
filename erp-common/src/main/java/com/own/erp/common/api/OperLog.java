package com.own.erp.common.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 操作审计标记(#27②):挂 Controller 写动作方法,由 erp-system OperLogAspect 环绕拦截落 sys_oper_log。
 *     只挂人工业务动作(订单审核/库存动账/发货/采购审核等),系统 Job 不挂——操作审计=人工动作,系统行为走 traceId+应用日志。
 *     进 erp-common 是因为注解要被各业务模块 Controller 引用(切面实现收口 erp-system,同事件范式:声明与实现分离)。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 业务模块:order/inventory/fulfill/purchase/finance */
    String module();

    /** 动作标识,如 review/ship/audit/confirm */
    String action();
}
