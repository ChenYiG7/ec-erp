package com.own.erp.purchase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.purchase.request.query.SupplierQuery;
import com.own.erp.purchase.request.command.SupplierSaveRequest;
import com.own.erp.purchase.response.SupplierResponse;
import com.own.erp.purchase.service.SupplierService;
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
 * @Description : 供应商管理:Supplier 域整域收口,一律走 SupplierService(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出 Service 层
 */
@Tag(name = "供应商", description = "供应商 CRUD 与分页查询")
@RestController
@RequestMapping("/api/purchase/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @Operation(summary = "分页查询供应商")
    @GetMapping
    public Result<Page<SupplierResponse>> page(SupplierQuery query) {
        return Result.ok(supplierService.page(query));
    }

    @Operation(summary = "供应商详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<SupplierResponse> get(@PathVariable Long id) {
        return Result.ok(supplierService.getById(id));
    }

    @Operation(summary = "新增供应商")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SupplierSaveRequest request) {
        return Result.ok(supplierService.save(request));
    }

    @Operation(summary = "更新供应商", description = "MP updateById 忽略 null 字段,可部分更新;id 只认路径参数")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierSaveRequest request) {
        supplierService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除供应商", description = "一期硬删")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.ok();
    }
}
