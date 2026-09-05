package com.own.erp.config;

import com.own.erp.system.auth.JwtProperties;
import com.own.erp.system.auth.JwtTokenService;
import com.own.erp.system.auth.LoginUser;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : JWT 认证过滤器(TODO#1):解析 Authorization: Bearer &lt;token&gt;,
 *     校验通过把 LoginUser 与 ROLE_xxx 权限写入 SecurityContext(role_key 即角色,供 @PreAuthorize hasRole 使用);
 *     token 缺失/无效不清场直接放行,由认证入口点统一返回 401
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(jwtProperties.getHeader());
        if (header != null && header.startsWith(jwtProperties.getBearerPrefix())) {
            String token = header.substring(jwtProperties.getBearerPrefix().length());
            try {
                LoginUser loginUser = jwtTokenService.parse(token);
                List<SimpleGrantedAuthority> authorities = loginUser.roleKeys().stream()
                        .map(roleKey -> new SimpleGrantedAuthority("ROLE_" + roleKey))
                        .toList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // 过期/被篡改/格式非法:按匿名继续,交给 AuthenticationEntryPoint 返回 401
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
