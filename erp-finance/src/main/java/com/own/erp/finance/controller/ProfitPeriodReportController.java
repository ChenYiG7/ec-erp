package com.own.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.finance.request.query.ProfitPeriodReportQuery;
import com.own.erp.finance.response.ProfitPeriodReportResponse;
import com.own.erp.finance.service.ProfitPeriodReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 周期利润报告接口(#19 三口径第二层):系统写入表只读查询(登录即可),
 *     无人工写入口——周期行由结算报告 PARSED 同事务派生 + Job 兜底重算(#32 校差 2026-09-12 已落地接线)
 */
@Tag(name = "周期利润报告", description = "结算报告期粒度:订单口径 vs 结算口径双侧对照与校差(系统写入,只读)")
@RestController
@RequestMapping("/api/finance/profit-periods")
@RequiredArgsConstructor
public class ProfitPeriodReportController {

    private final ProfitPeriodReportService profitPeriodReportService;

    @Operation(summary = "分页查询", description = "支持店铺/状态过滤;按周期止倒序")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<Page<ProfitPeriodReportResponse>> page(ProfitPeriodReportQuery query) {
        return Result.ok(profitPeriodReportService.page(query));
    }

    @Operation(summary = "周期详情", description = "双侧金额/校差/汇率快照;不存在返回 null data")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<ProfitPeriodReportResponse> get(@PathVariable Long id) {
        return Result.ok(profitPeriodReportService.getById(id));
    }
}
