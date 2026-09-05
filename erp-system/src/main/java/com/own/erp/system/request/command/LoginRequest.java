package com.own.erp.system.request.command;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 登录入参
 */
public record LoginRequest(String username, String password) {

    /** 密码不进日志,重写 toString 脱敏 */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}
