package com.own.erp.system.request.command;

import jakarta.validation.constraints.NotBlank;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 重置密码入参(管理员对指定用户操作)
 */
public record PasswordResetRequest(
        @NotBlank(message = "新密码不能为空") String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetRequest[newPassword=****]";
    }
}
