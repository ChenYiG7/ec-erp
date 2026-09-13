package com.own.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.OperLog;
import com.own.erp.common.api.Result;
import com.own.erp.inventory.request.command.TransferOrderSaveRequest;
import com.own.erp.inventory.request.query.TransferOrderQuery;
import com.own.erp.inventory.response.TransferOrderResponse;
import com.own.erp.inventory.service.TransferOrderService;
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
 * @Date : 2026/9/11
 * @Description : 调拨单管理:TransferOrder 域整域收口,一律走 TransferOrderService(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出 Service 层
 *     状态机接口:confirm(DRAFT→CONFIRMED,V1 确认即达:同事务两腿 TRANSFER_OUT/IN 动账,可用不足整单回滚)/
 *     cancel(仅草稿);调拨为仓内运营高频操作,登录即可(不限 admin,同发货单口径)
 */
@Tag(name = "调拨单", description = "调拨单 CRUD 与状态机(草稿/已确认/已取消);确认即达,确认后双腿动账")
@RestController
@RequestMapping("/api/inventory/transfer-orders")
@RequiredArgsConstructor
public class TransferOrderController {

    private final TransferOrderService transferOrderService;

    @Operation(summary = "分页查询调拨单")
    @GetMapping
    public Result<Page<TransferOrderResponse>> page(TransferOrderQuery query) {
        return Result.ok(transferOrderService.page(query));
    }

    @Operation(summary = "调拨单详情", description = "带明细;不存在返回 null data")
    @GetMapping("/{id}")
    public Result<TransferOrderResponse> get(@PathVariable Long id) {
        return Result.ok(transferOrderService.getById(id));
    }

    @Operation(summary = "新增调拨单", description = "DRAFT;调出仓须有足够可用库存方可在确认时通过")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody TransferOrderSaveRequest request) {
        return Result.ok(transferOrderService.save(request));
    }

    @Operation(summary = "更新调拨单", description = "仅草稿状态可改;明细整体替换")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody TransferOrderSaveRequest request) {
        transferOrderService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "确认调拨", description = "DRAFT→CONFIRMED(DIRECT)/DRAFT→IN_TRANSIT(在途模式);同事务逐行动账(biz_type=TRANSFER_ORDER),可用不足整单回滚")
    @OperLog(module = "inventory", action = "transfer-confirm")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        transferOrderService.confirm(id);
        return Result.ok();
    }

    @Operation(summary = "到货确认", description = "IN_TRANSIT→CONFIRMED(#30 在途模式);逐行调入仓 IN_TRANSFER 核销(在途转在库),按计划数全额核销,短少走盘点调整")
    @OperLog(module = "inventory", action = "transfer-receive")
    @PostMapping("/{id}/receive")
    public Result<Void> receive(@PathVariable Long id) {
        transferOrderService.receive(id);
        return Result.ok();
    }

    @Operation(summary = "取消调拨单", description = "仅草稿状态可取消;已确认库存已动账走反向调拨")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        transferOrderService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "删除调拨单", description = "已确认禁删(库存已动账);草稿/已取消连明细硬删")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        transferOrderService.delete(id);
        return Result.ok();
    }
}
