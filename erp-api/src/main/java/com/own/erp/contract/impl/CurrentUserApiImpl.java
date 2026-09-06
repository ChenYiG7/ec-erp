package com.own.erp.contract.impl;

import com.own.erp.contract.CurrentUserApi;
import com.own.erp.system.auth.AuthContext;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : CurrentUserApi 实现(接口模块方案的编排胶水,收口 erp-api,#10/#11 遗留 createdBy 收口):
 *         取数走 erp-system AuthContext(SecurityContext 中的 LoginUser,JwtAuthenticationFilter 装配)
 */
@Component
public class CurrentUserApiImpl implements CurrentUserApi {

    @Override
    public Long currentUserId() {
        return AuthContext.current().userId();
    }
}
