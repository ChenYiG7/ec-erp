package com.own.erp.contract.impl;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.auth.AuthContext;
import com.own.erp.system.auth.LoginUser;
import com.own.erp.system.service.SysUserShopService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : CurrentUserApiImpl 单测(AIR:真实 SecurityContextHolder 存取,不 mock 静态;
 *     SysUserShopService mock 不依赖数据库):登录态取 userId / 匿名抛 401 /
 *     currentShopIds 三态(#27①)——admin 返 null=不限(不触授权表)、
 *     非 admin 返授权集、非 admin 未授权返空列表=不可见。finally 清 Context 防线程复用串号
 */
class CurrentUserApiImplTest {

    private SysUserShopService userShopService;
    private CurrentUserApiImpl currentUserApi;

    @BeforeEach
    void setUp() {
        userShopService = mock(SysUserShopService.class);
        currentUserApi = new CurrentUserApiImpl(userShopService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login(LoginUser loginUser) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    @Test
    void returnsUserIdFromSecurityContext() {
        LoginUser loginUser = new LoginUser(9L, "admin", "管理员", List.of("admin"));
        login(loginUser);
        assertEquals(9L, currentUserApi.currentUserId());
    }

    @Test
    void rejectsWhenAnonymous() {
        BusinessException e = assertThrows(BusinessException.class, currentUserApi::currentUserId);
        assertEquals(401, e.getCode());
    }

    @Test
    void currentShopIdsAdminBypassesToUnrestricted() {
        login(new LoginUser(1L, "admin", "管理员", List.of("admin")));
        assertNull(currentUserApi.currentShopIds());
        verifyNoInteractions(userShopService);
    }

    @Test
    void currentShopIdsReturnsAuthorizedSetForRestrictedUser() {
        login(new LoginUser(9L, "op", "运营", List.of("operator")));
        when(userShopService.listShopIdsByUserId(9L)).thenReturn(List.of(2L, 5L));
        assertEquals(List.of(2L, 5L), currentUserApi.currentShopIds());
    }

    @Test
    void currentShopIdsEmptySetMeansNoVisibility() {
        login(new LoginUser(9L, "newbie", "新人", List.of("operator")));
        when(userShopService.listShopIdsByUserId(9L)).thenReturn(List.of());
        assertEquals(List.of(), currentUserApi.currentShopIds());
    }

    /** 调度/系统线程无认证上下文:返 null=不限(admin 语义),不触授权表(2026-09-12 预警冒烟修复) */
    @Test
    void currentShopIdsWithoutAuthContextReturnsUnrestricted() {
        assertNull(currentUserApi.currentShopIds());
        verifyNoInteractions(userShopService);
    }
}
