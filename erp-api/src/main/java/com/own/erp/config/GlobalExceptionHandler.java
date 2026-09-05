package com.own.erp.config;

import com.own.erp.common.api.Result;
import com.own.erp.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 统一异常出口:业务异常返回业务码,未知异常打日志返回 500,不向外暴露堆栈。
 *     AccessDeniedException(@PreAuthorize 方法级拒绝)在此转 403——
 *     它发生在 MVC 内部,走不到 SecurityConfig 的 accessDeniedHandler;AuthorizationDeniedException 是其子类,一并覆盖。
 *     BindException(@Valid 校验失败,MethodArgumentNotValidException 为其子类)转 400 + 字段明细,
 *     不落入兜底 500(docs/07 §1:XxxSaveRequest + @Valid 的统一出口)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleValidation(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(400, "参数校验失败: " + detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.fail(403, "无权限访问");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return Result.fail(500, "系统繁忙,请稍后重试");
    }
}
