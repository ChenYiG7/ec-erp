package com.own.erp.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.auth.JwtTokenService;
import com.own.erp.system.auth.LoginUser;
import com.own.erp.system.request.command.LoginRequest;
import com.own.erp.system.entity.SysUser;
import com.own.erp.system.mapper.SysUserMapper;
import com.own.erp.system.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 登录认证服务(TODO#1):用户名密码校验 + JWT 签发 + 当前用户信息(角色/权限/菜单)组装
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int STATUS_ENABLED = 1;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SysMenuService menuService;

    /** 登录:校验通过签发 JWT;用户不存在与密码错误返回同一文案,防用户名探测 */
    public LoginResponse login(LoginRequest request) {
        if (StrUtil.isBlank(request.username()) || StrUtil.isBlank(request.password())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(403, "账号已禁用,请联系管理员");
        }
        LoginResponse base = assemble(user);
        return base.withToken(jwtTokenService.create(
                new LoginUser(user.getId(), user.getUsername(), user.getNickname(), base.roles())));
    }

    /** 当前登录用户信息(不含 token):角色/权限/菜单树 */
    public LoginResponse me(LoginUser loginUser) {
        SysUser user = userMapper.selectById(loginUser.userId());
        if (user == null || user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(401, "账号不存在或已禁用");
        }
        return assemble(user);
    }

    /** 公共组装:角色标识 + 权限标识 + 可见菜单树(record builder 装配,docs/07 §1 模型可变性分级) */
    private LoginResponse assemble(SysUser user) {
        List<String> roleKeys = menuService.listRoleKeysByUserId(user.getId());
        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(roleKeys)
                .perms(menuService.listPermKeysByUserId(user.getId()))
                .menus(menuService.treeByUserId(user.getId()))
                .build();
    }
}
