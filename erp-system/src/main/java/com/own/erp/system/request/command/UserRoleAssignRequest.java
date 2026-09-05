package com.own.erp.system.request.command;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 用户-角色绑定入参:全量重绑(传空列表 = 清空该用户角色)
 */
public record UserRoleAssignRequest(List<Long> roleIds) {
}
