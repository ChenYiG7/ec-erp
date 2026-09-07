package com.own.erp.system.controller;

import com.own.erp.common.api.Result;
import com.own.erp.system.request.command.RoleMenuAssignRequest;
import com.own.erp.system.request.command.SysMenuSaveRequest;
import com.own.erp.system.response.SysMenuResponse;
import com.own.erp.system.service.SysMenuService;
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

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 菜单管理(TODO#1 RBAC)。菜单域整域收口(docs/07 §2.1)——读写一律走 SysMenuService,本类不注入 Mapper;
 *     系统管理类接口限 admin 角色(@PreAuthorize 按 role_key 鉴权)
 */
@Tag(name = "菜单管理", description = "菜单树与角色-菜单绑定;限 admin 角色")
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class SysMenuController {

    private final SysMenuService menuService;

    @Operation(summary = "全量菜单树", description = "含禁用节点(管理端用);用户侧边栏走 /api/auth/me")
    @GetMapping("/tree")
    public Result<List<SysMenuResponse>> tree() {
        return Result.ok(menuService.tree());
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SysMenuSaveRequest request) {
        return Result.ok(menuService.create(request));
    }

    @Operation(summary = "更新菜单", description = "MP updateById 忽略 null 字段,可部分更新;禁止把父级设成自身")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysMenuSaveRequest request) {
        menuService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除菜单", description = "有子菜单则拒绝;同事务清理角色-菜单引用")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "查看角色已绑定的菜单ID", description = "角色-菜单绑定回显")
    @GetMapping("/roles/{roleId}")
    public Result<List<Long>> roleMenus(@PathVariable Long roleId) {
        return Result.ok(menuService.roleMenuIds(roleId));
    }

    @Operation(summary = "角色-菜单全量重绑", description = "先删后插,同事务")
    @PutMapping("/roles/{roleId}")
    public Result<Void> assignRoleMenus(@PathVariable Long roleId,
                                        @Valid @RequestBody RoleMenuAssignRequest request) {
        menuService.assignMenusToRole(roleId, request.menuIds());
        return Result.ok();
    }
}
