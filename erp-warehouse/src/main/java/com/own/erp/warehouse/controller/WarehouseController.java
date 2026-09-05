package com.own.erp.warehouse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.warehouse.request.query.WarehouseQuery;
import com.own.erp.warehouse.request.command.WarehouseSaveRequest;
import com.own.erp.warehouse.response.WarehouseResponse;
import com.own.erp.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * @Date : 2026/9/3
 * @Description : 仓库管理:Warehouse 域整域收口,一律走 WarehouseService(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出 Service 层
 */
@Tag(name = "仓库", description = "仓库 CRUD 与分页查询")
@RestController
@RequestMapping("/api/warehouse/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @Operation(summary = "分页查询仓库")
    @GetMapping
    public Result<Page<WarehouseResponse>> page(WarehouseQuery query) {
        return Result.ok(warehouseService.page(query));
    }

    @Operation(summary = "仓库详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<WarehouseResponse> get(@PathVariable Long id) {
        return Result.ok(warehouseService.getById(id));
    }

    @Operation(summary = "新增仓库")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody WarehouseSaveRequest request) {
        return Result.ok(warehouseService.save(request));
    }

    @Operation(summary = "更新仓库", description = "MP updateById 忽略 null 字段,可部分更新;id 只认路径参数")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody WarehouseSaveRequest request) {
        warehouseService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除仓库", description = "一期硬删")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return Result.ok();
    }
}
