package com.own.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.inventory.request.query.InventoryQuery;
import com.own.erp.inventory.response.InventoryResponse;
import com.own.erp.inventory.service.InventoryService;
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
 * @Description : 库存查询:Inventory 域整域收口,一律走 InventoryService(docs/07 §2.1)
 *     数量列只能经 InventoryService.change() 变动(docs/07 铁律 4),不开放人工写接口
 */
@Tag(name = "库存", description = "分仓库存查询(变更唯一入口 InventoryService.change)")
@RestController
@RequestMapping("/api/inventory/inventories")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "分页查询库存")
    @GetMapping
    public Result<Page<InventoryResponse>> page(InventoryQuery query) {
        return Result.ok(inventoryService.page(query));
    }

    @Operation(summary = "库存详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<InventoryResponse> get(@PathVariable Long id) {
        return Result.ok(inventoryService.getById(id));
    }
}
