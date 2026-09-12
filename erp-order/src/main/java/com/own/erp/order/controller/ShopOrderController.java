package com.own.erp.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.OperLog;
import com.own.erp.common.api.Result;
import com.own.erp.contract.CurrentUserApi;
import com.own.erp.order.request.command.ManualOrderSaveRequest;
import com.own.erp.order.request.command.ShopOrderReviewCommand;
import com.own.erp.order.request.query.ShopOrderQuery;
import com.own.erp.order.response.ShopOrderResponse;
import com.own.erp.order.service.ManualOrderService;
import com.own.erp.order.service.ShopOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * @Description : 平台订单:ShopOrder 域整域收口,一律走 ShopOrderService/ManualOrderService(docs/07 §2.1)。
 *     对外只读纪律与三个人工业务动作端点的边界(#29 订单域补课):
 *     - 平台同步正本(shop_order)对外禁写——拉单走 OrderPullJob 系统链路,不开通用 CRUD;
 *     - 审核(POST /{id}/review)与内销录单(POST /manual、PUT /manual/{id})是**业务动作端点**
 *       (Service 收口 + 状态机/字段校验),不是开放通用写;
 *     - 按钮级权限由前端 permKey(order:review/order:manual)收口,后端登录即可(同发货单端点口径)
 */
@Tag(name = "平台订单", description = "平台订单查询、人工审核、内销订单录入(#29)")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class ShopOrderController {

    private final ShopOrderService shopOrderService;
    private final ManualOrderService manualOrderService;
    private final CurrentUserApi currentUserApi;

    @Operation(summary = "分页查询平台订单", description = "过滤:店铺/平台/订单状态/订单来源/审核状态(#29 扩两过滤);"
            + "数据权限(#27①):按当前用户授权店铺集过滤(GET 绑定后强制覆盖),admin 不限")
    @GetMapping
    public Result<Page<ShopOrderResponse>> page(ShopOrderQuery query) {
        query.setShopIds(currentUserApi.currentShopIds());
        return Result.ok(shopOrderService.page(query));
    }

    @Operation(summary = "平台订单详情", description = "不存在返回 null data")
    @GetMapping("/{id}")
    public Result<ShopOrderResponse> get(@PathVariable Long id) {
        return Result.ok(shopOrderService.getById(id));
    }

    @Operation(summary = "订单审核", description = "状态机:待审核(1)/无需审核(0)/已驳回(3) → 通过(2)/驳回(3);"
            + "已通过为审核终态;审核人/时间服务端回填,remark 落审核备注(#29)")
    @OperLog(module = "order", action = "review")
    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ShopOrderReviewCommand command) {
        shopOrderService.review(id, command.approve(), command.remark());
        return Result.ok();
    }

    @Operation(summary = "内销订单录入", description = "合成单号 MAN-*;status 固定 WAIT_SHIP、来源 MANUAL、"
            + "金额服务端计算;SKU 必绑;按风控规则决定是否待审核。返回订单ID(#29)")
    @PostMapping("/manual")
    public Result<Long> createManual(@Valid @RequestBody ManualOrderSaveRequest request) {
        return Result.ok(manualOrderService.create(request));
    }

    @Operation(summary = "修改内销订单", description = "仅 MANUAL + WAIT_SHIP 可改;明细整体替换并重算金额;"
            + "不触碰单号/状态/审核列(#29)")
    @PutMapping("/manual/{id}")
    public Result<Void> updateManual(@PathVariable Long id, @Valid @RequestBody ManualOrderSaveRequest request) {
        manualOrderService.update(id, request);
        return Result.ok();
    }
}
