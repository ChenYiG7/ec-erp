package com.own.erp.finance.controller;

import com.own.erp.common.api.PageQuery;
import com.own.erp.common.api.Result;
import com.own.erp.finance.entity.ExchangeRate;
import com.own.erp.finance.request.query.ExchangeRateQuery;
import com.own.erp.finance.response.ExchangeRateResponse;
import com.own.erp.finance.service.ExchangeRateService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 汇率快照接口(#19③ 利润核算 V1 数据面):读侧登录即可(利润报表页选币参考),
 *     写侧财务数据 admin 双闸(@PreAuthorize hasRole('admin') + 菜单 perm_key 前端收口,SystemConfigController 同款);
 *     V1 仅 MANUAL 手工维护,折算消费侧一律走 ExchangeRateService.resolveRate 回溯口径
 */
@Tag(name = "汇率快照", description = "多币种折算依据:1 currency = rate CNY;利润折算按业务日回溯取最近报价")
@RestController
@RequestMapping("/api/finance/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @Operation(summary = "分页查询", description = "按报价时间倒序;过滤币种")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<Page<ExchangeRateResponse>> page(ExchangeRateQuery query) {
        return Result.ok(exchangeRateService.page(query));
    }

    @Operation(summary = "手工录入快照", description = "currency/rate/quotedAt 必填,汇率>0;source 固定 MANUAL;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<Long> save(@RequestBody ExchangeRate rate) {
        return Result.ok(exchangeRateService.save(rate));
    }
}
