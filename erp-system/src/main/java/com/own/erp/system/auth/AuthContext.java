package com.own.erp.system.auth;

import com.own.erp.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 业务侧取当前登录用户的统一入口。
 *     JwtAuthenticationFilter(erp-api)校验通过后已把 LoginUser 放入 SecurityContext
 */
public final class AuthContext {

    private AuthContext() {
    }

    /** 当前登录用户;未登录抛 401(理论上过滤器已拦截,防御性兜底) */
    public static LoginUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        throw new BusinessException(401, "未登录或登录已过期");
    }
}
