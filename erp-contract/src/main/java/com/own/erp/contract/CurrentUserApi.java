package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 当前登录用户契约(接口模块方案):人工单据 createdBy 审计列回填(#10/#11 遗留收口,2026-09-06)。
 *         业务域禁横向依赖 erp-system(铁律 2),实现收口 erp-api(CurrentUserApiImpl,经 erp-system AuthContext
 *         取 JwtAuthenticationFilter 写入 SecurityContext 的 LoginUser);SaveRequest 同步剔除 createdBy 入参,
 *         服务端权威回填。仅 HTTP 登录链路可用——Job/系统写入链路无登录态,勿在系统链路调用
 */
public interface CurrentUserApi {

    /** 当前登录用户 ID(sys_user.id);未登录抛 401(对齐 AuthContext.current 语义,过滤器已拦,防御性兜底) */
    Long currentUserId();
}
