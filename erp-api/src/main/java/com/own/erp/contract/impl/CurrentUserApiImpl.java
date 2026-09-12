package com.own.erp.contract.impl;

import com.own.erp.contract.CurrentUserApi;
import com.own.erp.system.auth.AuthContext;
import com.own.erp.system.auth.LoginUser;
import com.own.erp.system.service.SysUserShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : CurrentUserApi 实现(接口模块方案的编排胶水,收口 erp-api,#10/#11 遗留 createdBy 收口):
 *         取数走 erp-system AuthContext(SecurityContext 中的 LoginUser,JwtAuthenticationFilter 装配)。
 *         currentShopIds(#27① 数据权限方案A):admin 角色短路返 null=不限(不查授权表);
 *         非 admin 查 sys_user_shop 授权集,空集=不可见任何店铺数据。每次调用实时查库,
 *         授权变更即时生效(JWT payload 不动,#27 拍板:避免 token 窗口内权限漂移)。
 *         无认证上下文(AlertJob 调度线程等系统侧取数)返 null=不限,admin 语义——
 *         HTTP 链路有 JwtAuthenticationFilter 拦截,无上下文只可能是系统内部调用(2026-09-12 预警冒烟修复)
 */
@Component
@RequiredArgsConstructor
public class CurrentUserApiImpl implements CurrentUserApi {

    /** 超管角色键(RBAC role_key,对齐 @PreAuthorize("hasRole('admin')") 口径) */
    private static final String ROLE_ADMIN = "admin";

    private final SysUserShopService userShopService;

    @Override
    public Long currentUserId() {
        return AuthContext.current().userId();
    }

    @Override
    public List<Long> currentShopIds() {
        LoginUser user = AuthContext.currentOrNull();
        if (user == null) {
            // 调度/系统线程无认证上下文:null=不限(#27① admin 语义),系统侧取数不受店铺数据权限钳制
            return null;
        }
        if (user.roleKeys() != null && user.roleKeys().contains(ROLE_ADMIN)) {
            return null;
        }
        return userShopService.listShopIdsByUserId(user.userId());
    }
}
