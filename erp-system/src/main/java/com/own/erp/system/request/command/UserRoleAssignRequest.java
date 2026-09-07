package com.own.erp.system.request.command;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 用户-角色绑定入参:全量重绑(传空列表 = 清空该用户角色)
 */
public record UserRoleAssignRequest(

        /** 允许空列表=清空全部角色;null 视为非法载荷 */
        @NotNull(message = "角色ID列表不能为null")
        List<Long> roleIds) {
}
