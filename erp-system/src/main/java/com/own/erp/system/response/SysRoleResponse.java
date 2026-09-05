package com.own.erp.system.response;

import com.own.erp.system.entity.SysRole;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 角色对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 */
@Builder
public record SysRoleResponse(

        /** 主键 */
        Long id,

        /** 角色名称 */
        String roleName,

        /** 权限标识,唯一 */
        String roleKey,

        /** 1=启用 0=禁用 */
        Integer status,

        /** 备注 */
        String remark,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static SysRoleResponse from(SysRole role) {
        return SysRoleResponse.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleKey(role.getRoleKey())
                .status(role.getStatus())
                .remark(role.getRemark())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
