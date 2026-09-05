package com.own.erp.purchase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.purchase.request.query.PurchaseOrderQuery;
import com.own.erp.purchase.request.command.PurchaseOrderSaveRequest;
import com.own.erp.purchase.response.PurchaseOrderResponse;
import com.own.erp.purchase.service.PurchaseOrderService;
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
 * @Date : 2026/9/3
 * @Description : 采购单管理:PurchaseOrder 域整域收口,一律走 PurchaseOrderService(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出 Service 层
 *     状态机接口(#10):审核/关闭类管理操作按 role_key 鉴权(@PreAuthorize,当前仅 admin,细粒度采购角色随权限规划)
 */
@Tag(name = "采购单", description = "采购单 CRUD、审核/关闭状态机与分页查询")
@RestController
@RequestMapping("/api/purchase/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @Operation(summary = "分页查询采购单", description = "列表不带明细")
    @GetMapping
    public Result<Page<PurchaseOrderResponse>> page(PurchaseOrderQuery query) {
        return Result.ok(purchaseOrderService.page(query));
    }

    @Operation(summary = "采购单详情", description = "带采购明细;不存在返回 null data")
    @GetMapping("/{id}")
    public Result<PurchaseOrderResponse> get(@PathVariable Long id) {
        return Result.ok(purchaseOrderService.getById(id));
    }

    @Operation(summary = "新增采购单", description = "status 服务端固定草稿,totalAmount 按 Σ(数量×单价) 服务端计算")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PurchaseOrderSaveRequest request) {
        return Result.ok(purchaseOrderService.save(request));
    }

    @Operation(summary = "更新采购单", description = "仅草稿状态可修改,明细整体替换并重算金额;id 只认路径参数")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderSaveRequest request) {
        purchaseOrderService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除采购单", description = "仅草稿且无入库记录可删;其余状态走关闭流程;一期硬删")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "审核采购单", description = "状态机:草稿→已审核,审核后可创建入库单")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id) {
        purchaseOrderService.audit(id);
        return Result.ok();
    }

    @Operation(summary = "关闭采购单", description = "状态机:已审核/部分入库/已入库→已关闭,剩余未收量作废;草稿单请走删除")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        purchaseOrderService.close(id);
        return Result.ok();
    }
}
