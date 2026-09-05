package com.own.erp.system.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.auth.JwtTokenService;
import com.own.erp.system.auth.LoginUser;
import com.own.erp.system.entity.SysUser;
import com.own.erp.system.mapper.SysUserMapper;
import com.own.erp.system.request.command.LoginRequest;
import com.own.erp.system.response.LoginResponse;
import com.own.erp.system.response.SysMenuResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/5
 * @Description : AuthService 单测(AIR:mock Mapper/JWT/菜单服务,PasswordEncoder 真实 BCrypt 低代价轮次,
 *     docs/07 §10)。重点:用户不存在与密码错误同一文案(防用户名探测)/禁用账号 403/JWT 载荷装配/
 *     /me 不携带 token
 */
class AuthServiceTest {

    private SysUserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtTokenService jwtTokenService;
    private SysMenuService menuService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtTokenService = mock(JwtTokenService.class);
        menuService = mock(SysMenuService.class);
        service = new AuthService(userMapper, passwordEncoder, jwtTokenService, menuService);
    }

    private SysUser enabledUser() {
        return SysUser.builder().id(7L).username("chen").nickname("陈")
                .password(passwordEncoder.encode("real-Pass")).status(1).build();
    }

    private void stubAssemble() {
        when(menuService.listRoleKeysByUserId(7L)).thenReturn(List.of("admin"));
        when(menuService.listPermKeysByUserId(7L)).thenReturn(List.of("system:user:list"));
        when(menuService.treeByUserId(7L)).thenReturn(List.of(
                SysMenuResponse.builder().id(1L).menuName("系统管理").build()));
    }

    @Test
    void loginRejectsBlankCredentials() {
        assertThrows(BusinessException.class, () -> service.login(new LoginRequest(null, "x")));
        assertThrows(BusinessException.class, () -> service.login(new LoginRequest("chen", " ")));
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    void loginRejectsUnknownUserAndWrongPasswordWithSameMessage() {
        // 防用户名探测:两种失败返回同一文案同一 code
        when(userMapper.selectOne(any())).thenReturn(null);
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("ghost", "whatever")));
        when(userMapper.selectOne(any())).thenReturn(enabledUser());
        BusinessException wrongPwd = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("chen", "bad-Pass")));

        assertEquals(401, unknown.getCode());
        assertEquals(unknown.getMessage(), wrongPwd.getMessage());
        assertEquals(unknown.getCode(), wrongPwd.getCode());
    }

    @Test
    void loginRejectsDisabledUser() {
        SysUser disabled = SysUser.builder().id(7L).username("chen")
                .password(passwordEncoder.encode("real-Pass")).status(0).build();
        when(userMapper.selectOne(any())).thenReturn(disabled);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("chen", "real-Pass")));

        assertEquals(403, ex.getCode());
        verify(jwtTokenService, never()).create(any());
    }

    @Test
    void loginIssuesTokenWithAssembledProfile() {
        when(userMapper.selectOne(any())).thenReturn(enabledUser());
        stubAssemble();
        when(jwtTokenService.create(any())).thenReturn("jwt-token");

        LoginResponse response = service.login(new LoginRequest("chen", "real-Pass"));

        assertEquals("jwt-token", response.token());
        assertEquals(7L, response.userId());
        assertEquals("chen", response.username());
        assertEquals(List.of("admin"), response.roles());
        assertEquals(List.of("system:user:list"), response.perms());
        assertEquals("系统管理", response.menus().get(0).menuName());
        // JWT 载荷:身份 + 角色标识,禁放密码/权限明细(docs/07 §7)
        ArgumentCaptor<LoginUser> captor = ArgumentCaptor.forClass(LoginUser.class);
        verify(jwtTokenService).create(captor.capture());
        assertEquals(7L, captor.getValue().userId());
        assertEquals("chen", captor.getValue().username());
        assertEquals("陈", captor.getValue().nickname());
        assertEquals(List.of("admin"), captor.getValue().roleKeys());
    }

    @Test
    void meRejectsMissingOrDisabledUser() {
        when(userMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.me(new LoginUser(404L, "ghost", null, List.of())));

        SysUser disabled = SysUser.builder().id(7L).username("chen").status(0).build();
        when(userMapper.selectById(7L)).thenReturn(disabled);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.me(new LoginUser(7L, "chen", null, List.of())));
        assertEquals(401, ex.getCode());
    }

    @Test
    void meAssemblesProfileWithoutToken() {
        when(userMapper.selectById(7L)).thenReturn(enabledUser());
        stubAssemble();

        LoginResponse response = service.me(new LoginUser(7L, "chen", "陈", List.of("admin")));

        // /me 不签发 token,权限/菜单实时查(token 只含身份与角色)
        assertNull(response.token());
        assertEquals(List.of("admin"), response.roles());
        assertEquals(List.of("system:user:list"), response.perms());
        assertEquals(1, response.menus().size());
        verify(jwtTokenService, never()).create(any());
    }
}
