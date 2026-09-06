package com.own.erp.contract.impl;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.auth.AuthContext;
import com.own.erp.system.auth.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : CurrentUserApiImpl 单测(AIR:真实 SecurityContextHolder 存取,不 mock 静态态):
 *     登录态取 userId / 匿名(无认证)抛 401。finally 清 Context 防线程复用串号(同 TraceIdFilter 纪律)
 */
class CurrentUserApiImplTest {

    private final CurrentUserApiImpl currentUserApi = new CurrentUserApiImpl();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsUserIdFromSecurityContext() {
        LoginUser loginUser = new LoginUser(9L, "admin", "管理员", List.of("admin"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
        assertEquals(9L, currentUserApi.currentUserId());
    }

    @Test
    void rejectsWhenAnonymous() {
        BusinessException e = assertThrows(BusinessException.class, currentUserApi::currentUserId);
        assertEquals(401, e.getCode());
    }
}
