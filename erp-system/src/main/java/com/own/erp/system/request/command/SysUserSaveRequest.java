package com.own.erp.system.request.command;

import com.own.erp.system.entity.SysUser;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 用户写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)。
 *     password 仅创建时必填(BCrypt 加密落库),更新时忽略——改密只能走 resetPassword/changePassword 专用通道;
 *     toEntity 刻意不映射 password,更新路径物理隔绝(经此绕过 BCrypt 的可能在编译期即封死)
 */
@Builder
public record SysUserSaveRequest(

        /** 登录名,唯一 */
        @NotBlank(message = "用户名不能为空")
        String username,

        /** 初始密码,仅创建时必填;更新时忽略 */
        String password,

        /** 昵称 */
        String nickname,

        /** 邮箱 */
        String email,

        /** 手机号 */
        String phone,

        /** 1=启用 0=禁用 */
        Integer status
) {

    /** 手写 toString 脱敏(docs/07 §1):record 自动 toString 携带全部组件,password 禁入日志 */
    @Override
    public String toString() {
        return "SysUserSaveRequest[username=" + username + ", password=****, nickname=" + nickname
                + ", email=" + email + ", phone=" + phone + ", status=" + status + "]";
    }

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);password 不映射,只经 Service 专用通道写入 */
    public SysUser toEntity() {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(status);
        return user;
    }
}
