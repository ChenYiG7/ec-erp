package com.own.erp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 安全配置(TODO#1):
 *     - 无状态 JWT:除登录外所有接口需认证;系统管理类接口在 Controller 上按 role_key 用 @PreAuthorize 收口
 *     - 401/403 统一返回 Result JSON(与业务返回体同构),错误码与 HTTP 状态一致,前端好统一处理
 *     - 密码加密(BCrypt)定义在 erp-system 的 PasswordConfig,与过滤链解耦
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 预检请求与登录接口放行;/error 放行避免错误转发被 401 吞掉真实状态码;
                        // OAuth 回调放行(#3):平台授权页重定向卖家浏览器直跳,无 JWT,防伪造靠加密 state(TTL 10 分钟)
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers("/api/auth/login", "/api/shops/oauth/callback", "/error").permitAll()
                        // 接口文档(swagger-ui/knife4j 联调入口,不在 /api 红线内);生产可 yml 关 springdoc.api-docs.enabled
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 开发期放开跨域(JWT 走 Header 不依赖 Cookie);前端上线前按域名收紧 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 未登录(无 token/过期/非法):401 + Result JSON */
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException e) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期");
    }

    /** 已登录但权限不足:403 + Result JSON */
    private void writeForbidden(HttpServletRequest request, HttpServletResponse response,
                                AccessDeniedException e) throws IOException {
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, "无权限访问");
    }

    /** 手写 JSON,避免引入 ObjectMapper 依赖差异;结构与 Result 一致 */
    private void writeJson(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":" + status + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
