package com.own.erp.system.request.command;

import com.own.erp.system.entity.SysRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 角色写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)
 */
@Builder
public record SysRoleSaveRequest(

        /** 角色名称 */
        @NotBlank(message = "角色名称不能为空")
        String roleName,

        /** 权限标识,唯一,如 admin / operator */
        @NotBlank(message = "权限标识不能为空")
        String roleKey,

        /** 1=启用 0=禁用 */
        Integer status,

        /** 备注 */
        String remark
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝) */
    public SysRole toEntity() {
        SysRole role = new SysRole();
        role.setRoleName(roleName);
        role.setRoleKey(roleKey);
        role.setStatus(status);
        role.setRemark(remark);
        return role;
    }
}
