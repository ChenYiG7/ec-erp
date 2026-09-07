package com.own.erp.system.request.command;

import jakarta.validation.constraints.NotBlank;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 登录入参
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password) {

    /** 密码不进日志,重写 toString 脱敏 */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}
