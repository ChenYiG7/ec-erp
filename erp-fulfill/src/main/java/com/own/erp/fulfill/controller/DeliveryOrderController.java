package com.own.erp.fulfill.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.fulfill.request.query.DeliveryOrderQuery;
import com.own.erp.fulfill.request.command.DeliveryOrderSaveRequest;
import com.own.erp.fulfill.response.DeliveryOrderResponse;
import com.own.erp.fulfill.service.DeliveryOrderService;
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
 * @Description : 发货单管理:DeliveryOrder 域整域收口,一律走 DeliveryOrderService(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出 Service 层
 *     状态机接口(#11):ship 确认发货(同事务出库动账+订单推进)/cancel 取消/deliver 签收;
 *     发货是运营高频操作不限 admin(审核类管理操作鉴权先例见 PurchaseOrderController)
 */
@Tag(name = "发货单", description = "发货单 CRUD、分页查询与状态机(待发货/已发货/已签收/已取消)")
@RestController
@RequestMapping("/api/fulfill/delivery-orders")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;

    @Operation(summary = "分页查询发货单")
    @GetMapping
    public Result<Page<DeliveryOrderResponse>> page(DeliveryOrderQuery query) {
        return Result.ok(deliveryOrderService.page(query));
    }

    @Operation(summary = "发货单详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<DeliveryOrderResponse> get(@PathVariable Long id) {
        return Result.ok(deliveryOrderService.getById(id));
    }

    @Operation(summary = "新增发货单", description = "仅待发货(WAIT_SHIP)且卖家自履约订单可建;明细行须 sku_id 已绑定")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody DeliveryOrderSaveRequest request) {
        return Result.ok(deliveryOrderService.save(request));
    }

    @Operation(summary = "更新发货单", description = "仅待发货状态可改;明细整体替换;不允许变更关联订单")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeliveryOrderSaveRequest request) {
        deliveryOrderService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "确认发货", description = "PENDING→SHIPPED,同事务逐行出库扣减库存(OUT_SHIP),全部订单明细发足时推进订单 WAIT_SHIP→SHIPPED")
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        deliveryOrderService.ship(id);
        return Result.ok();
    }

    @Operation(summary = "取消发货单", description = "仅待发货状态可取消;已发货库存已动账走售后退货(#12)")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        deliveryOrderService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "标记签收", description = "SHIPPED→DELIVERED;一期人工签收,平台物流轨迹自动签收随 #3 adapter 评估")
    @PostMapping("/{id}/deliver")
    public Result<Void> deliver(@PathVariable Long id) {
        deliveryOrderService.markDelivered(id);
        return Result.ok();
    }

    @Operation(summary = "删除发货单", description = "一期硬删;已发货/已签收禁删(库存已动账)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deliveryOrderService.delete(id);
        return Result.ok();
    }
}
