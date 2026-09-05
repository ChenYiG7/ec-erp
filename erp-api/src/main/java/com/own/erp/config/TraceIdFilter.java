package com.own.erp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 链路追踪过滤器:为每个 HTTP 请求生成/透传 traceId 写入 MDC(logback pattern 引用 %X{traceId}),
 *     并以 X-Trace-Id 响应头返回——前端报障凭 traceId 即可定位整条日志;异常日志(GlobalExceptionHandler)自动携带。
 *     优先级设为最高,确保先于 Spring Security 过滤链,安全日志同样带 traceId。
 *     约定:上游可带 X-Trace-Id 请求头(网关/前端重试链路复用同一 traceId),不带则服务端生成;
 *     finally 必清 MDC——Tomcat 线程复用,残留会串请求。
 *     TODO(#3 拉单定时任务):@Scheduled 线程不经本过滤器,任务入口自行 MDC.put/close(拉单 shopId 维度日志本就落 pull_log,双保险)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";
    /** 16 位 hex:分钟级百万请求内碰撞可忽略,日志列宽可控 */
    private static final int TRACE_ID_LENGTH = 16;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = newTraceId();
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }
}
