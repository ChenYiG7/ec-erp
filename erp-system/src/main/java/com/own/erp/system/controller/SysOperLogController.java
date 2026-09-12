package com.own.erp.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.system.request.query.SysOperLogQuery;
import com.own.erp.system.response.SysOperLogResponse;
import com.own.erp.system.service.SysOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : 操作审计查询(#27②):sys_oper_log 只增流水对外仅只读,整域收口走 SysOperLogService(docs/07 §2.1);
 *     系统管理类接口限 admin 角色(同部门/菜单管理口径);无写端点——审计事实禁人工改删
 */
@Tag(name = "操作日志", description = "人工业务动作审计查询,只读;限 admin 角色(#27②)")
@RestController
@RequestMapping("/api/system/oper-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class SysOperLogController {

    private final SysOperLogService operLogService;

    @Operation(summary = "操作日志分页", description = "过滤参数:username(模糊)/module/resultStatus/时间窗(ISO LocalDateTime);时间倒序")
    @GetMapping
    public Result<Page<SysOperLogResponse>> page(SysOperLogQuery query) {
        return Result.ok(operLogService.page(query));
    }
}
