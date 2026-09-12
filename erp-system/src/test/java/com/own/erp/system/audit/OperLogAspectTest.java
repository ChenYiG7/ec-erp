package com.own.erp.system.audit;

import tools.jackson.databind.ObjectMapper;
import com.own.erp.common.api.OperLog;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.system.auth.LoginUser;
import com.own.erp.system.event.SysOperLogEvent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : OperLogAspect 单测(#27②,AIR:mock 连接点,不依赖 web 容器):
 *     OK/FAIL 两态采集、用户快照、路径变量 bizId、X-Forwarded-For 取 IP、MDC traceId 透传、
 *     敏感字段名掩码(password/accessToken/嵌套 Map)、params 2KB 截断、业务异常原样重抛
 */
class OperLogAspectTest {

    private ApplicationEventPublisher eventPublisher;
    private OperLogAspect aspect;

    /** 模拟 @RequestBody record:password/accessToken 命中脱敏黑名单,nickname 不命中 */
    private record TestCommand(Long orderId, String nickname, String password, String accessToken) {
    }

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        aspect = new OperLogAspect(eventPublisher, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(9L, "admin", "管理员", List.of("admin")), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private ProceedingJoinPoint joinPoint(String[] paramNames, Object[] args, Object proceedResult) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(paramNames);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(proceedResult);
        return joinPoint;
    }

    private OperLog operLog(String module, String action) {
        OperLog annotation = mock(OperLog.class);
        when(annotation.module()).thenReturn(module);
        when(annotation.action()).thenReturn(action);
        return annotation;
    }

    private SysOperLogEvent capturedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return (SysOperLogEvent) captor.getValue();
    }

    @Test
    void okActionCapturesUserModuleAndParams() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(
                new String[]{"id", "command"},
                new Object[]{5L, new TestCommand(5L, "张三", "plain-pwd", "tok-123")},
                null);

        aspect.around(joinPoint, operLog("order", "review"));

        SysOperLogEvent event = capturedEvent();
        assertEquals(9L, event.userId());
        assertEquals("admin", event.username());
        assertEquals("order", event.module());
        assertEquals("review", event.action());
        assertEquals("order", event.bizType());
        assertEquals("OK", event.resultStatus());
        assertNull(event.errorMsg());
        assertTrue(event.costMs() >= 0);
        assertTrue(event.paramsJson().contains("\"orderId\":5"));
        assertTrue(event.paramsJson().contains("张三"));
        assertFalse(event.paramsJson().contains("plain-pwd"));
        assertFalse(event.paramsJson().contains("tok-123"));
        assertTrue(event.paramsJson().contains("******"));
    }

    @Test
    void failActionRecordsErrorAndRethrows() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"id"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L});
        when(joinPoint.proceed()).thenThrow(new BusinessException(400, "库存不足"));

        assertThrows(BusinessException.class, () -> aspect.around(joinPoint, operLog("fulfill", "ship")));

        SysOperLogEvent event = capturedEvent();
        assertEquals("FAIL", event.resultStatus());
        assertEquals("库存不足", event.errorMsg());
    }

    @Test
    void capturesIpBizIdAndTraceIdFromRequest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "77"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("traceId", "trace0123456789ab");

        ProceedingJoinPoint joinPoint = joinPoint(new String[]{"id"}, new Object[]{77L}, null);
        aspect.around(joinPoint, operLog("inventory", "transfer-confirm"));

        SysOperLogEvent event = capturedEvent();
        assertEquals("203.0.113.9", event.ip());
        assertEquals(77L, event.bizId());
        assertEquals("trace0123456789ab", event.traceId());
    }

    @Test
    void masksNestedSensitiveKeysAndTruncatesTo2kb() throws Throwable {
        Map<String, Object> nested = Map.of("appKey", "secret-app-key", "memo", "普通值");
        String longText = "x".repeat(5000);
        ProceedingJoinPoint joinPoint = joinPoint(
                new String[]{"id", "payload", "text"},
                new Object[]{1L, nested, longText},
                null);

        aspect.around(joinPoint, operLog("purchase", "audit"));

        SysOperLogEvent event = capturedEvent();
        assertFalse(event.paramsJson().contains("secret-app-key"));
        assertTrue(event.paramsJson().contains("普通值"));
        assertTrue(event.paramsJson().length() <= 2048);
    }

    @Test
    void unpublishedWhenUserAbsentStillCapturesNullUser() throws Throwable {
        SecurityContextHolder.clearContext();
        ProceedingJoinPoint joinPoint = joinPoint(new String[]{"id"}, new Object[]{1L}, null);

        aspect.around(joinPoint, operLog("purchase", "close"));

        SysOperLogEvent event = capturedEvent();
        assertNull(event.userId());
        assertNull(event.username());
        assertEquals("purchase", event.module());
    }
}
