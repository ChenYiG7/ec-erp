package com.own.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.finance.request.command.FirstLegShipRequest;
import com.own.erp.finance.request.command.FirstLegShipmentSaveRequest;
import com.own.erp.finance.request.query.FirstLegShipmentQuery;
import com.own.erp.finance.response.FirstLegShipmentResponse;
import com.own.erp.finance.response.FirstLegSkuAllocRow;
import com.own.erp.finance.service.FirstLegShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程发货单接口(#33 头程运费分摊):装箱数据面 + 状态机动作 + 运费分摊 + 查询面,
 *     一律走 FirstLegShipmentService(docs/07 §2.1 Controller 不直连 Mapper);entity 不出 Service。
 *     读侧登录即可;建改删/状态推进属财务写操作,admin 双闸——@PreAuthorize hasRole('admin')
 *     + 菜单 perm_key 前端收口(同资金流水口径)
 */
@Tag(name = "头程发货单", description = "头程装箱/发货/运费分摊到SKU:列表详情、建改删、装箱、发货录运费、分摊、关闭、取消与SKU费用汇总")
@RestController
@RequestMapping("/api/finance/first-leg-shipments")
@RequiredArgsConstructor
public class FirstLegShipmentController {

    private final FirstLegShipmentService firstLegShipmentService;

    @Operation(summary = "头程发货单分页", description = "单号模糊/状态/发货仓/目的仓过滤,双仓名联表投影")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<Page<FirstLegShipmentResponse>> page(FirstLegShipmentQuery query) {
        return Result.ok(firstLegShipmentService.page(query));
    }

    @Operation(summary = "头程发货单详情", description = "主单 + 装箱树(箱/内件,带SKU编码)+ 分摊结果行;不存在返回 null data")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<FirstLegShipmentResponse> get(@PathVariable Long id) {
        return Result.ok(firstLegShipmentService.getById(id));
    }

    @Operation(summary = "SKU维度头程费用汇总", description = "Σ ALLOCATED/CLOSED 单分摊按 SKU 聚合(CNY),可按 SKU/发货时间过滤;利润第三层聚合源")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sku-allocs")
    public Result<List<FirstLegSkuAllocRow>> skuAllocs(
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shippedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shippedTo) {
        return Result.ok(firstLegShipmentService.listSkuAllocSummary(skuId, shippedFrom, shippedTo));
    }

    @Operation(summary = "新建头程发货单", description = "草稿态建单,可同时带装箱(箱+内件);发货仓必须SELF,目的仓必须OVERSEAS/FBA;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody FirstLegShipmentSaveRequest request) {
        return Result.ok(firstLegShipmentService.save(request));
    }

    @Operation(summary = "修改头程发货单", description = "仅草稿态可改,装箱整体替换;已装箱后改单走取消重建;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody FirstLegShipmentSaveRequest request) {
        firstLegShipmentService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除头程发货单", description = "仅草稿/已取消可物理删除(连带箱与内件);已发货起禁删;限 admin")
    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        firstLegShipmentService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "装箱完成", description = "DRAFT→BOXED;至少 1 箱且箱内有 SKU;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/box")
    public Result<Void> box(@PathVariable Long id) {
        firstLegShipmentService.box(id);
        return Result.ok();
    }

    @Operation(summary = "确认发货并录运费", description = "BOXED→SHIPPED;运费>0,非CNY需有汇率报价或手填汇率,无报价拦截;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id,
                             @Valid @RequestBody FirstLegShipRequest request) {
        firstLegShipmentService.ship(id, request);
        return Result.ok();
    }

    @Operation(summary = "执行运费分摊", description = "SHIPPED→ALLOCATED;按数量/重量/金额三策略摊到SKU,全0基数自动降级按数量,部分缺基数拦截;不可重算;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/allocate")
    public Result<Void> allocate(@PathVariable Long id) {
        firstLegShipmentService.allocate(id);
        return Result.ok();
    }

    @Operation(summary = "关闭头程单", description = "ALLOCATED→CLOSED;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        firstLegShipmentService.close(id);
        return Result.ok();
    }

    @Operation(summary = "取消头程单", description = "仅草稿/已装箱可取消;已发货起运费已录不可取消;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        firstLegShipmentService.cancel(id);
        return Result.ok();
    }
}
