package com.own.erp.system.controller;

import com.own.erp.common.api.Result;
import com.own.erp.system.auth.AuthContext;
import com.own.erp.system.request.command.LoginRequest;
import com.own.erp.system.service.AuthService;
import com.own.erp.system.response.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 认证接口(TODO#1)。/api/auth/login 在 SecurityConfig 中放行,其余接口需携带
 *     Authorization: Bearer &lt;token&gt; 访问
 */
@Tag(name = "认证", description = "登录签发 JWT 与当前用户信息")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录:校验通过签发 JWT */
    @Operation(summary = "登录", description = "SecurityConfig 唯一放行的写接口;签发 JWT,有效期 24h(erp.jwt.expire-hours)")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "当前登录用户", description = "角色/权限/菜单树(无 token 字段);请求头需 Authorization: Bearer <token>")
    @GetMapping("/me")
    public Result<LoginResponse> me() {
        return Result.ok(authService.me(AuthContext.current()));
    }
}
