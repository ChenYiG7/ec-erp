package com.own.erp.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.OperLog;
import com.own.erp.common.api.Result;
import com.own.erp.inventory.request.command.StocktakeCountRequest;
import com.own.erp.inventory.request.command.StocktakeOrderSaveRequest;
import com.own.erp.inventory.request.query.StocktakeOrderQuery;
import com.own.erp.inventory.response.StocktakeOrderResponse;
import com.own.erp.inventory.service.StocktakeOrderService;
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
 * @Description : 盘点单管理:StocktakeOrder 域整域收口,一律走 StocktakeOrderService(docs/07 §2.1)
 *     API 模型收口(docs/07 §1):读入参 query/XxxQuery、写入参 command/XxxSaveRequest(CQRS 分包),出参 response/XxxResponse,entity 不出 Service 层
 *     状态机接口:DRAFT→COUNTING(start)/COUNTING→PENDING_ADJUST(submit)/PENDING_ADJUST→ADJUSTED(adjust,
 *     复合事务:确认时点 re-diff + 逐行 ADJUST 动账)/ADJUSTED→CLOSED(close)/cancel(未动账三态);
 *     盘点为仓内运营高频操作,登录即可(不限 admin,同发货单口径)
 */
@Tag(name = "盘点单", description = "盘点单 CRUD 与状态机(草稿/盘点中/待调整/已调整/已关闭/已取消)")
@RestController
@RequestMapping("/api/inventory/stocktakes")
@RequiredArgsConstructor
public class StocktakeOrderController {

    private final StocktakeOrderService stocktakeOrderService;

    @Operation(summary = "分页查询盘点单")
    @GetMapping
    public Result<Page<StocktakeOrderResponse>> page(StocktakeOrderQuery query) {
        return Result.ok(stocktakeOrderService.page(query));
    }

    @Operation(summary = "盘点单详情", description = "带明细;不存在返回 null data")
    @GetMapping("/{id}")
    public Result<StocktakeOrderResponse> get(@PathVariable Long id) {
        return Result.ok(stocktakeOrderService.getById(id));
    }

    @Operation(summary = "新增盘点单", description = "DRAFT 建单即快照账面在库;scopeType=ALL 全仓/SKU_SET 选定 SKU 集")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody StocktakeOrderSaveRequest request) {
        return Result.ok(stocktakeOrderService.save(request));
    }

    @Operation(summary = "更新盘点单", description = "仅草稿状态可改;明细整体重做快照")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody StocktakeOrderSaveRequest request) {
        stocktakeOrderService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "开始盘点", description = "DRAFT→COUNTING;进入录实盘阶段")
    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        stocktakeOrderService.start(id);
        return Result.ok();
    }

    @Operation(summary = "录入实盘", description = "COUNTING 阶段逐行录 counted_qty,允许多次补录/修正")
    @PutMapping("/{id}/counts")
    public Result<Void> recordCounts(@PathVariable Long id, @Valid @RequestBody StocktakeCountRequest request) {
        stocktakeOrderService.recordCounts(id, request);
        return Result.ok();
    }

    @Operation(summary = "提交盘点", description = "COUNTING→PENDING_ADJUST;所有明细行实盘录齐方可通过")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        stocktakeOrderService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "生成调整", description = "PENDING_ADJUST→ADJUSTED;按确认时点账面 re-diff,差异行经 InventoryService.change(ADJUST) 动账,可用不足整单回滚")
    @OperLog(module = "inventory", action = "stocktake-adjust")
    @PostMapping("/{id}/adjust")
    public Result<Void> adjust(@PathVariable Long id) {
        stocktakeOrderService.generateAdjust(id);
        return Result.ok();
    }

    @Operation(summary = "关闭盘点单", description = "ADJUSTED→CLOSED(终态);只有已调整可达,保证差异必处理或明确放弃")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        stocktakeOrderService.close(id);
        return Result.ok();
    }

    @Operation(summary = "取消盘点单", description = "未动账三态(草稿/盘点中/待调整)→CANCELED;已调整/已关闭不可取消")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        stocktakeOrderService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "删除盘点单", description = "已调整/已关闭禁删(差异已动账);其余状态连明细硬删")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        stocktakeOrderService.delete(id);
        return Result.ok();
    }
}
