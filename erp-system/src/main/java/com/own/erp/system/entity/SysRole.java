package com.own.erp.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 角色(sys_role)。RBAC 的菜单/接口授权(sys_menu、sys_role_menu)属复杂逻辑,见 TODO.md #1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色名称 */
    private String roleName;

    /** 权限标识,唯一,如 admin / operator */
    private String roleKey;

    /** 1=启用 0=禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id);delval=id 配合唯一键含 deleted,删后同键可重建(TODO#7) */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
