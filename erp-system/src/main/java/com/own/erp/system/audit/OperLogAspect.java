package com.own.erp.system.audit;

import tools.jackson.databind.ObjectMapper;
import com.own.erp.common.api.OperLog;
import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.system.auth.AuthContext;
import com.own.erp.system.auth.LoginUser;
import com.own.erp.system.event.SysOperLogEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 操作审计切面(#27②):环绕 @OperLog 标记的 Controller 写动作,采集操作人/参数/结果/耗时后
 *     发布 {@link SysOperLogEvent},由 SysOperLogService AFTER_COMMIT 落库——本类不做任何 IO,
 *     发布失败只记日志不影响业务返回(审计失败不阻塞业务铁律,docs/plans/27-rbac-enhance.md §2.2)。
 *     切面在 Controller 方法返回后运行,业务事务(Service @Transactional)此时已提交;
 *     发布事件用 plain publish,落库时机由监听器 AFTER_COMMIT + fallbackExecution 相位保证
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 参数 JSON 截断上限(计划书 §4:params_json 截断 2KB) */
    private static final int PARAMS_MAX_LENGTH = 2048;

    /** 异常摘要截断上限(列宽 error_msg VARCHAR(500)) */
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    /** IP 截断上限(列宽 VARCHAR(64),防伪造头超列宽) */
    private static final int IP_MAX_LENGTH = 64;

    /** 敏感字段名黑名单(命中即值掩码,口径同 docs/07 §7 SECRET 掩码):字段名包含任一片段即命中 */
    private static final Set<String> SENSITIVE_KEY_FRAGMENTS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "appkey",
            "accesskey", "authorization", "credential", "privatekey");

    /** 无 JSON 序列化价值的框架入参类型,直接跳过不进 params_json */
    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            publish(joinPoint, operLog, "OK", null, costMs(start));
            return result;
        } catch (Throwable ex) {
            publish(joinPoint, operLog, "FAIL", summarize(ex), costMs(start));
            throw ex;
        }
    }

    private void publish(ProceedingJoinPoint joinPoint, OperLog operLog,
                         String resultStatus, String errorMsg, int costMs) {
        try {
            LoginUser user = currentUser();
            HttpServletRequest request = currentRequest();
            eventPublisher.publishEvent(new SysOperLogEvent(
                    user == null ? null : user.userId(),
                    user == null ? null : user.username(),
                    operLog.module(),
                    operLog.action(),
                    operLog.module(),
                    pathVariableId(request),
                    serializeParams(joinPoint),
                    resultStatus,
                    errorMsg,
                    resolveIp(request),
                    MDC.get(TRACE_ID_MDC_KEY),
                    costMs));
        } catch (Exception ex) {
            log.warn("操作审计事件发布失败(不影响业务): module={} action={}", operLog.module(), operLog.action(), ex);
        }
    }

    private LoginUser currentUser() {
        try {
            return AuthContext.current();
        } catch (Exception ex) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /** 路径变量 id 作 bizId({id} 路由形态统一约定),缺失或非数字返回 null */
    private Long pathVariableId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object uriVars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVars instanceof Map<?, ?> vars && vars.get("id") != null) {
            try {
                return Long.parseLong(String.valueOf(vars.get("id")));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 参数名 → JSON:框架类型跳过;不可序列化参数降级 toString;敏感字段名值掩码;整体截断 2KB */
    private String serializeParams(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (paramNames == null || args.length == 0) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null || skipped(arg)) {
                continue;
            }
            String name = i < paramNames.length ? paramNames[i] : ("arg" + i);
            params.put(name, convertible(arg));
        }
        if (params.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(maskSensitive(params));
            return truncate(json, PARAMS_MAX_LENGTH);
        } catch (Exception ex) {
            log.warn("操作审计参数序列化失败,params 置空: signature={}", signature.toShortString(), ex);
            return null;
        }
    }

    private String truncate(String text, int maxLength) {
        return text != null && text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private boolean skipped(Object arg) {
        return arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                || arg instanceof MultipartFile || arg instanceof BindingResult;
    }

    /** record/POJO 走 Jackson 归一;不可序列化参数(如流)降级 toString 防序列化异常中断采集 */
    private Object convertible(Object arg) {
        try {
            return objectMapper.convertValue(arg, Object.class);
        } catch (Exception ex) {
            return truncate(String.valueOf(arg), PARAMS_MAX_LENGTH);
        }
    }

    /** 递归掩码:字段名命中黑名单即整值替换为 SECRET_MASK(凭证口径复用 docs/07 §7) */
    private Map<String, Object> maskSensitive(Map<String, Object> params) {
        Map<String, Object> masked = new LinkedHashMap<>(params.size());
        params.forEach((key, value) -> {
            if (isSensitiveKey(key)) {
                masked.put(key, ConfigConsts.SECRET_MASK);
            } else if (value instanceof Map<?, ?> nested) {
                masked.put(key, maskSensitive(copyOf(nested)));
            } else if (value instanceof List<?> list) {
                masked.put(key, list.stream()
                        .map(item -> item instanceof Map<?, ?> m ? maskSensitive(copyOf(m)) : item)
                        .toList());
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }

    private Map<String, Object> copyOf(Map<?, ?> source) {
        Map<String, Object> copy = new HashMap<>();
        source.forEach((k, v) -> copy.put(String.valueOf(k), v));
        return copy;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        return SENSITIVE_KEY_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    /** 操作人 IP:X-Forwarded-For 首段(代理链最原始客户端)优先,截断防超列宽 */
    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return ip != null && ip.length() > IP_MAX_LENGTH ? ip.substring(0, IP_MAX_LENGTH) : ip;
    }

    private String summarize(Throwable ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return message.length() > ERROR_MSG_MAX_LENGTH ? message.substring(0, ERROR_MSG_MAX_LENGTH) : message;
    }

    private int costMs(long start) {
        return (int) Math.min(System.currentTimeMillis() - start, Integer.MAX_VALUE);
    }
}
