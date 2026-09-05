package com.own.erp.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.PageQuery;
import com.own.erp.common.api.Result;
import com.own.erp.system.request.command.SysRoleSaveRequest;
import com.own.erp.system.response.SysRoleResponse;
import com.own.erp.system.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 角色 CRUD(TODO#1 RBAC)。角色域整域收口(docs/07 §2.1)——读写一律走 SysRoleService;
 *     菜单/用户的绑定关系在 SysMenuController / SysUserController;管理类接口限 admin 角色。
 *     API 模型收口(docs/07 §1):写入参 command/SysRoleSaveRequest,出参 response/SysRoleResponse
 */
@Tag(name = "角色管理", description = "角色 CRUD;限 admin 角色")
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "分页查询角色", description = "无过滤条件,分页参数直接用 PageQuery(pageNo/pageSize 钳制)")
    @GetMapping
    public Result<Page<SysRoleResponse>> page(PageQuery query) {
        return Result.ok(roleService.pageRoles(query));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SysRoleSaveRequest request) {
        return Result.ok(roleService.createRole(request));
    }

    @Operation(summary = "更新角色", description = "MP updateById 忽略 null 字段,可部分更新")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysRoleSaveRequest request) {
        roleService.updateRole(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除角色", description = "仍绑定用户则拒绝;同事务清理角色-菜单绑定")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.ok();
    }
}
