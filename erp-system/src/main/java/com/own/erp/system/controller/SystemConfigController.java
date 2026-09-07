package com.own.erp.system.controller;

import com.own.erp.common.api.Result;
import com.own.erp.system.entity.SysConfig;
import com.own.erp.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 系统参数接口(#18 系统设置):读侧登录即可(前端系统设置页按组渲染表单),
 *     写侧 admin 双闸(@PreAuthorize hasRole('admin') + 菜单 perm_key 前端收口,SysUserController 同款)。
 *     保存语义:upsert 覆盖值,空值 = 删覆盖行回落代码默认值;保存后契约缓存即时失效(30s TTL 内的
 *     Job 取值最迟 30 秒生效),模型连接/提示词等消费侧取值点即时感知
 */
@Tag(name = "系统参数", description = "启动后可变项运行时配置:大模型/预警阈值/AI 工作流参数;保存即时生效(短缓存)")
@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "按组取参数", description = "返回该组合法键全量(合并代码默认占位 + DB 覆盖值),前端按行渲染表单")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/group/{group}")
    public Result<List<SysConfig>> listByGroup(@PathVariable String group) {
        return Result.ok(systemConfigService.listByGroup(group));
    }

    @Operation(summary = "保存一组参数", description = "upsert 覆盖值;空值=删覆盖行回落默认值;键必须在该组词表白名单内,类型校验失败拒存;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PutMapping("/group/{group}")
    public Result<Integer> saveGroup(@PathVariable String group,
                                     @RequestBody Map<String, String> values) {
        return Result.ok(systemConfigService.saveGroup(group, values));
    }
}
