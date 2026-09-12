package com.own.erp.system.controller;

import com.own.erp.common.api.Result;
import com.own.erp.system.request.command.SysDeptSaveRequest;
import com.own.erp.system.service.SysDeptService;
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
 * @Date : 2026/9/12
 * @Description : 部门管理(#27③):部门域整域收口(docs/07 §2.1)——树组装与 CRUD 一律走 SysDeptService。
 *     API 模型收口(docs/07 §1):入参 command/SysDeptSaveRequest,出参为树节点 DeptNode,
 *     entity 不出 Service 层;系统管理类接口限 admin 角色(同菜单/角色管理口径)
 */
@Tag(name = "部门管理", description = "部门树与 CRUD;限 admin 角色(#27③)")
@RestController
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class SysDeptController {

    private final SysDeptService deptService;

    @Operation(summary = "全量部门树", description = "一次查全量内存组树,按 sort 升序,parent_id=0 为根;含禁用节点(管理端用)")
    @GetMapping("/tree")
    public Result<List<SysDeptService.DeptNode>> tree() {
        return Result.ok(deptService.tree());
    }

    @Operation(summary = "新增部门", description = "parentId 不传按根处理;同名同级由 uk(parent_id,dept_name,deleted) 排他")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SysDeptSaveRequest request) {
        return Result.ok(deptService.create(request));
    }

    @Operation(summary = "更新部门", description = "MP updateById 忽略 null 字段,可部分更新;禁止把父级设成自身或自己的子孙(成环校验)")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysDeptSaveRequest request) {
        deptService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除部门", description = "有子部门或被用户引用时拦截")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }
}
