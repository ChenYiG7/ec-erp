package com.own.erp.system.response;

import com.own.erp.system.entity.SysUser;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 用户对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)——
 *     password(BCrypt 哈希)不建字段即编译期封死,任何返回路径物理隔离
 */
@Builder
public record SysUserResponse(

        /** 主键 */
        Long id,

        /** 登录名 */
        String username,

        /** 昵称 */
        String nickname,

        /** 邮箱 */
        String email,

        /** 手机号 */
        String phone,

        /** 1=启用 0=禁用 */
        Integer status,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static SysUserResponse from(SysUser user) {
        return SysUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
