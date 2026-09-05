package com.own.erp.aftersale.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.aftersale.request.command.AftersaleHandleRequest;
import com.own.erp.aftersale.request.command.AftersaleReturnReceiveRequest;
import com.own.erp.aftersale.request.query.AftersaleOrderQuery;
import com.own.erp.aftersale.response.AftersaleOrderResponse;
import com.own.erp.aftersale.service.AftersaleOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 售后单管理:AftersaleOrder 域整域收口,一律走 AftersaleOrderService(docs/07 §2.1)
 *     系统写入表:数据由平台同步落库,不开放人工 CRUD 写接口;人工侧 = 只读查询 + 状态机处理动作(#12)
 *     处理动作是客服/运营高频操作不限 admin(对齐 #11 发货先例;审核类从严先例见 PurchaseOrderController)
 */
@Tag(name = "售后单", description = "售后单查询与状态机处理(#12:待处理/已同意/待收退件/已收退件/已退款/已完成/已拒绝/已取消)")
@RestController
@RequestMapping("/api/aftersale/orders")
@RequiredArgsConstructor
public class AftersaleOrderController {

    private final AftersaleOrderService aftersaleOrderService;

    @Operation(summary = "分页查询售后单", description = "支持 shopId/status/type/orderId 精确过滤")
    @GetMapping
    public Result<Page<AftersaleOrderResponse>> page(AftersaleOrderQuery query) {
        return Result.ok(aftersaleOrderService.page(query));
    }

    @Operation(summary = "售后单详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<AftersaleOrderResponse> get(@PathVariable Long id) {
        return Result.ok(aftersaleOrderService.getById(id));
    }

    @Operation(summary = "同意售后", description = "PENDING→APPROVED(仅退款/补发)/ RETURNING(退货退款/换货,等买家寄回);result 选填")
    @PostMapping("/{id}/agree")
    public Result<Void> agree(@PathVariable Long id, @RequestBody AftersaleHandleRequest request) {
        aftersaleOrderService.agree(id, request.result());
        return Result.ok();
    }

    @Operation(summary = "拒绝售后", description = "PENDING→REJECTED(终态);result 必填(拒绝原因留痕)")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody AftersaleHandleRequest request) {
        aftersaleOrderService.reject(id, request.result());
        return Result.ok();
    }

    @Operation(summary = "收退件 + 退货入库",
            description = "RETURNING→RETURN_RECEIVED,复合事务(#12):按实收明细逐行 IN_RETURN 动账入库并留痕;"
                    + "warehouseId/items 必填,result 选填(可记验件情况)")
    @PostMapping("/{id}/receive-return")
    public Result<Void> receiveReturn(@PathVariable Long id, @Valid @RequestBody AftersaleReturnReceiveRequest request) {
        aftersaleOrderService.receiveReturn(id, request);
        return Result.ok();
    }

    @Operation(summary = "退款", description = "APPROVED(仅退款/补发)/ RETURN_RECEIVED(退货类,须已收退件)→REFUNDED;result 选填")
    @PostMapping("/{id}/refund")
    public Result<Void> refund(@PathVariable Long id, @RequestBody AftersaleHandleRequest request) {
        aftersaleOrderService.refund(id, request.result());
        return Result.ok();
    }

    @Operation(summary = "确认完成", description = "REFUNDED→COMPLETED(终态收尾);result 选填")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestBody AftersaleHandleRequest request) {
        aftersaleOrderService.complete(id, request.result());
        return Result.ok();
    }
}
