package com.own.erp.system.request.command;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 角色-菜单绑定入参:全量重绑(传空列表 = 清空该角色菜单)
 */
public record RoleMenuAssignRequest(

        /** 允许空列表=清空全部授权;null 视为非法载荷 */
        @NotNull(message = "菜单ID列表不能为null")
        List<Long> menuIds) {
}
