package com.own.erp.shop.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.shop.request.query.PullLogQuery;
import com.own.erp.shop.response.PullLogResponse;
import com.own.erp.shop.service.PullLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 拉取日志查询:PullLog 域整域收口,一律走 PullLogService(docs/07 §2.1)
 *     日志由拉单任务写入(#4),只读,不开放人工写接口
 */
@Tag(name = "拉取日志", description = "平台拉取日志查询(增量游标依据)")
@RestController
@RequestMapping("/api/pull-logs")
@RequiredArgsConstructor
public class PullLogController {

    private final PullLogService pullLogService;

    @Operation(summary = "分页查询拉取日志")
    @GetMapping
    public Result<Page<PullLogResponse>> page(PullLogQuery query) {
        return Result.ok(pullLogService.page(query));
    }

    @Operation(summary = "拉取日志详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<PullLogResponse> get(@PathVariable Long id) {
        return Result.ok(pullLogService.getById(id));
    }
}
