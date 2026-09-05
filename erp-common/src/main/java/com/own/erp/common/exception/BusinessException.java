package com.own.erp.common.exception;

import lombok.Getter;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 业务异常,由全局异常处理器统一转换为 Result
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(500, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
