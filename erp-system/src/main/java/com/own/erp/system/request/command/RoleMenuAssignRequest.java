package com.own.erp.system.request.command;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 角色-菜单绑定入参:全量重绑(传空列表 = 清空该角色菜单)
 */
public record RoleMenuAssignRequest(List<Long> menuIds) {
}
