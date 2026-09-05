package com.own.erp.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.system.auth.AuthContext;
import com.own.erp.system.request.command.PasswordChangeRequest;
import com.own.erp.system.request.command.PasswordResetRequest;
import com.own.erp.system.request.command.SysUserSaveRequest;
import com.own.erp.system.request.command.UserRoleAssignRequest;
import com.own.erp.system.request.query.SysUserQuery;
import com.own.erp.system.response.SysUserResponse;
import com.own.erp.system.service.SysMenuService;
import com.own.erp.system.service.SysUserService;
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
 * @Description : 用户管理(TODO#1)。用户域整域收口(docs/07 §2.1)——读写一律走 SysUserService,本类不注入 Mapper。
 *     API 模型收口(docs/07 §1):入参 query/SysUserQuery、command/SysUserSaveRequest,出参 response/SysUserResponse,
 *     entity 不出 Service 层;password 无出参字段即编译期封死。
 *     管理类接口限 admin 角色;密码只存 BCrypt 哈希
 */
@Tag(name = "用户管理", description = "用户 CRUD 与密码管理;限 admin 角色(本人改密除外)")
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class SysUserController {

    private final SysUserService userService;
    private final SysMenuService menuService;

    @Operation(summary = "分页查询用户", description = "username 模糊匹配(可选),status 精确过滤(可选);password 不回传")
    @GetMapping
    public Result<Page<SysUserResponse>> page(SysUserQuery query) {
        return Result.ok(userService.pageUsers(query));
    }

    @Operation(summary = "用户详情", description = "password 不回传;不存在返回 null data")
    @GetMapping("/{id}")
    public Result<SysUserResponse> get(@PathVariable Long id) {
        return Result.ok(userService.getUserById(id));
    }

    @Operation(summary = "新增用户", description = "用户名唯一;初始密码必填并 BCrypt 加密落库")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SysUserSaveRequest request) {
        return Result.ok(userService.createUser(request));
    }

    @Operation(summary = "更新用户", description = "MP updateById 忽略 null 字段,可部分更新;password 不在写侧入参映射内,改密走专用接口")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysUserSaveRequest request) {
        userService.updateUser(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除用户", description = "同事务清理用户-角色绑定,防孤儿关联")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }

    @Operation(summary = "重置密码", description = "管理员对指定用户直接覆盖为新密码哈希")
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request.newPassword());
        return Result.ok();
    }

    @Operation(summary = "本人改密", description = "校验原密码后覆盖;无需 admin 角色(方法级覆盖类级注解)")
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@RequestBody PasswordChangeRequest request) {
        userService.changePassword(AuthContext.current().userId(),
                request.oldPassword(), request.newPassword());
        return Result.ok();
    }

    @Operation(summary = "查看用户已绑定的角色ID")
    @GetMapping("/{id}/roles")
    public Result<List<Long>> roles(@PathVariable Long id) {
        return Result.ok(menuService.listRoleIdsByUserId(id));
    }

    @Operation(summary = "用户-角色全量重绑", description = "先删后插,同事务")
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id,
                                    @RequestBody UserRoleAssignRequest request) {
        menuService.assignRolesToUser(id, request.roleIds());
        return Result.ok();
    }
}
