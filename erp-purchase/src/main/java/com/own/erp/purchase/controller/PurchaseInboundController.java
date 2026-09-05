package com.own.erp.purchase.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.purchase.request.query.PurchaseInboundQuery;
import com.own.erp.purchase.request.command.PurchaseInboundSaveRequest;
import com.own.erp.purchase.response.PurchaseInboundResponse;
import com.own.erp.purchase.service.PurchaseInboundService;
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
 * @Description : 采购入库单管理:PurchaseInbound 域整域收口,一律走 PurchaseInboundService(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出 Service 层
 *     核销接口(#10):确认入库为唯一动库存的入库路径(经 InventoryChangeApi 走 InventoryService.change 唯一入口),
 *     管理操作按 role_key 鉴权(@PreAuthorize,当前仅 admin)
 */
@Tag(name = "采购入库单", description = "采购入库单 CRUD、确认核销/取消与分页查询")
@RestController
@RequestMapping("/api/purchase/inbounds")
@RequiredArgsConstructor
public class PurchaseInboundController {

    private final PurchaseInboundService purchaseInboundService;

    @Operation(summary = "分页查询采购入库单", description = "列表不带明细")
    @GetMapping
    public Result<Page<PurchaseInboundResponse>> page(PurchaseInboundQuery query) {
        return Result.ok(purchaseInboundService.page(query));
    }

    @Operation(summary = "采购入库单详情", description = "带入库明细;不存在返回 null data")
    @GetMapping("/{id}")
    public Result<PurchaseInboundResponse> get(@PathVariable Long id) {
        return Result.ok(purchaseInboundService.getById(id));
    }

    @Operation(summary = "新增采购入库单", description = "status 服务端固定待入库,入库仓取采购单收货仓;明细须 ≤ 采购明细剩余未收量")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody PurchaseInboundSaveRequest request) {
        return Result.ok(purchaseInboundService.save(request));
    }

    @Operation(summary = "更新采购入库单", description = "仅待入库状态可修改,明细整体替换;不允许变更关联采购单;id 只认路径参数")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PurchaseInboundSaveRequest request) {
        purchaseInboundService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除采购入库单", description = "已入库(库存已动账)禁删;一期硬删")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        purchaseInboundService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "确认入库", description = "核销:同事务完成 库存变更(flow_type=IN_PURCHASE)+ 采购明细 arrived_qty 回写 + 采购单状态推进")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        purchaseInboundService.confirm(id);
        return Result.ok();
    }

    @Operation(summary = "取消入库单", description = "仅待入库可取消,终态不可逆")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        purchaseInboundService.cancel(id);
        return Result.ok();
    }
}
