package com.own.erp.common.api;

import lombok.Data;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 统一返回体
 */
@Data
public class Result<T> {

    public static final int CODE_OK = 200;
    public static final int CODE_FAIL = 500;

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = CODE_OK;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(String msg) {
        return fail(CODE_FAIL, msg);
    }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
