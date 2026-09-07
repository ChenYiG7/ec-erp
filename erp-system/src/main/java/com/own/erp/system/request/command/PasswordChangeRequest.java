package com.own.erp.system.request.command;

import jakarta.validation.constraints.NotBlank;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 本人改密入参(校验旧密码)
 */
public record PasswordChangeRequest(
        @NotBlank(message = "旧密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空") String newPassword) {

    @Override
    public String toString() {
        return "PasswordChangeRequest[oldPassword=****, newPassword=****]";
    }
}
