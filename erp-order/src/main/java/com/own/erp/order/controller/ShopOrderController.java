package com.own.erp.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.order.request.query.ShopOrderQuery;
import com.own.erp.order.response.ShopOrderResponse;
import com.own.erp.order.service.ShopOrderService;
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
 * @Description : 平台订单查询:ShopOrder 域整域收口,一律走 ShopOrderService(docs/07 §2.1)
 *     订单由拉单任务落库(#4),不开放人工新增/修改/删除接口
 */
@Tag(name = "平台订单", description = "平台订单查询(拉单落库,不开放人工写接口)")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class ShopOrderController {

    private final ShopOrderService shopOrderService;

    @Operation(summary = "分页查询平台订单")
    @GetMapping
    public Result<Page<ShopOrderResponse>> page(ShopOrderQuery query) {
        return Result.ok(shopOrderService.page(query));
    }

    @Operation(summary = "平台订单详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<ShopOrderResponse> get(@PathVariable Long id) {
        return Result.ok(shopOrderService.getById(id));
    }
}
