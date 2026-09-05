package com.own.erp.system.request.command;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 本人改密入参(校验旧密码)
 */
public record PasswordChangeRequest(String oldPassword, String newPassword) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[oldPassword=****, newPassword=****]";
    }
}
