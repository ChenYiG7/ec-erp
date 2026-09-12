package com.own.erp.contract;

import java.util.List;

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

    /**
     * 数据权限授权店铺集(#27① 方案A:店铺轴,2026-09-12)。
     * 语义三态(消费方必须全判,禁把 null 当空集或反之):
     * <ul>
     *   <li>{@code null} = 不限——admin 角色(实现按角色短路,不查授权表);</li>
     *   <li>非 null 非空 = 仅可见集合内店铺;</li>
     *   <li>非 null 空列表 = 不可见任何店铺数据(非 admin 用户未授权,见 sys_user_shop 表注释)。</li>
     * </ul>
     * 与 currentUserId 同源约束:仅 HTTP 登录链路可用,Job/系统链路禁调(铁律);
     * 消费方(契约 QueryApi 实现层 / 各域查询 Controller)强制装配进查询过滤,
     * 不信任调用方自带的 shop 过滤参数(防越权传参)
     */
    List<Long> currentShopIds();
}
