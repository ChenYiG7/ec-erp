package com.own.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.inventory.request.query.InventoryFlowQuery;
import com.own.erp.inventory.response.InventoryFlowResponse;
import com.own.erp.inventory.service.InventoryFlowService;
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
 * @Description : 库存流水查询:InventoryFlow 域整域收口,一律走 InventoryFlowService(docs/07 §2.1)
 *     流水只增不改不删,写入唯一入口 InventoryService.change(),不开放任何人工写接口
 */
@Tag(name = "库存流水", description = "库存变更流水查询(与库存变更同事务写入)")
@RestController
@RequestMapping("/api/inventory/flows")
@RequiredArgsConstructor
public class InventoryFlowController {

    private final InventoryFlowService inventoryFlowService;

    @Operation(summary = "分页查询库存流水")
    @GetMapping
    public Result<Page<InventoryFlowResponse>> page(InventoryFlowQuery query) {
        return Result.ok(inventoryFlowService.page(query));
    }

    @Operation(summary = "库存流水详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<InventoryFlowResponse> get(@PathVariable Long id) {
        return Result.ok(inventoryFlowService.getById(id));
    }
}
