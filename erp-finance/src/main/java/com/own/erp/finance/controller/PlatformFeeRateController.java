package com.own.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.finance.request.command.PlatformFeeRateSaveRequest;
import com.own.erp.finance.request.query.PlatformFeeRateQuery;
import com.own.erp.finance.response.PlatformFeeRateResponse;
import com.own.erp.finance.service.PlatformFeeRateService;
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
 * @Date : 2026/9/11
 * @Description : 平台费率表接口(#19 预估费用模型):读侧登录即可(利润页/周期页展示费率维度),
 *     写侧财务主数据 admin 双闸——@PreAuthorize hasRole('admin') + 菜单 perm_key 前端收口
 *     (同汇率快照 ExchangeRateController 口径);逻辑删除禁物理删
 */
@Tag(name = "平台费率表", description = "预估费用模型:结算未回按费率估佣金,结算回后校差;无费率不估算")
@RestController
@RequestMapping("/api/finance/fee-rates")
@RequiredArgsConstructor
public class PlatformFeeRateController {

    private final PlatformFeeRateService platformFeeRateService;

    @Operation(summary = "分页查询", description = "支持平台/费种过滤;同维按生效起倒序")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<Page<PlatformFeeRateResponse>> page(PlatformFeeRateQuery query) {
        return Result.ok(platformFeeRateService.page(query));
    }

    @Operation(summary = "费率详情", description = "不存在返回 null data")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<PlatformFeeRateResponse> get(@PathVariable Long id) {
        return Result.ok(platformFeeRateService.getById(id));
    }

    @Operation(summary = "新增费率", description = "platform/feeType/rate/effFrom 必填;V1 仅 COMMISSION,0<rate<1;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PlatformFeeRateSaveRequest request) {
        return Result.ok(platformFeeRateService.save(request));
    }

    @Operation(summary = "更新费率", description = "MP updateById 忽略 null 字段;source 不接受修改;id 只认路径参数;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PlatformFeeRateSaveRequest request) {
        platformFeeRateService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除费率", description = "逻辑删除(deleted=id),删后同维度同生效日可重建;限 admin")
    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        platformFeeRateService.delete(id);
        return Result.ok();
    }
}
