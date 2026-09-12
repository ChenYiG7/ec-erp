package com.own.erp.fulfill.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.OperLog;
import com.own.erp.common.api.Result;
import com.own.erp.fulfill.request.command.FbaReceiveRequest;
import com.own.erp.fulfill.request.command.FbaShipmentSaveRequest;
import com.own.erp.fulfill.request.query.FbaShipmentQuery;
import com.own.erp.fulfill.response.FbaShipmentResponse;
import com.own.erp.fulfill.service.FbaShipmentService;
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
 * @Date : 2026/9/12
 * @Description : FBA 发货单接口(docs/plans/fba-shipment.md,V1 内部数据面):查询面 + 建改删 + 状态机动作
 *     (装箱/发出动账/收货登记/关闭/取消),一律走 FbaShipmentService(docs/07 §2.1 Controller 不直连 Mapper);
 *     entity 不出 Service。鉴权由 erp-api SecurityConfig /api/** JWT 收口(本模块不引 security 依赖,同发货单口径),
 *     权限由菜单 perm_key(fulfill:fba:*)前端收口;人工动作挂 @OperLog(#27② 操作审计)
 */
@Tag(name = "FBA发货单", description = "FBA发货计划/装箱/发出动账/平台收货对账:列表详情、建改删、装箱完成、确认发出、收货登记、关闭与取消")
@RestController
@RequestMapping("/api/fulfill/fba-shipments")
@RequiredArgsConstructor
public class FbaShipmentController {

    private final FbaShipmentService fbaShipmentService;

    @Operation(summary = "FBA发货单分页", description = "单号模糊/状态/店铺/站点/发货仓过滤,店名仓名联表投影")
    @GetMapping
    public Result<Page<FbaShipmentResponse>> page(FbaShipmentQuery query) {
        return Result.ok(fbaShipmentService.page(query));
    }

    @Operation(summary = "FBA发货单详情", description = "主单 + 计划行 + 装箱树(箱/内件,带SKU编码)+ 对账差异行;不存在返回 null data")
    @GetMapping("/{id}")
    public Result<FbaShipmentResponse> get(@PathVariable Long id) {
        return Result.ok(fbaShipmentService.getById(id));
    }

    @Operation(summary = "新建FBA发货单", description = "草稿态建单:表头 + 计划行(SKU清单)+ 装箱(可后补);发货仓必须SELF;限 permKey fulfill:fba:add")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody FbaShipmentSaveRequest request) {
        return Result.ok(fbaShipmentService.save(request));
    }

    @Operation(summary = "修改FBA发货单", description = "仅草稿态可改,计划行/装箱整体替换;已装箱后改单走取消重建")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody FbaShipmentSaveRequest request) {
        fbaShipmentService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除FBA发货单", description = "仅草稿/已取消可物理删除(连带计划行/箱/内件);已发出起库存已动账禁删")
    @OperLog(module = "fulfill", action = "fba-delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fbaShipmentService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "装箱完成", description = "DRAFT→BOXED;至少 1 箱有件且逐SKU Σ箱内件=计划量(预检),发出时权威复检")
    @OperLog(module = "fulfill", action = "fba-box")
    @PostMapping("/{id}/box")
    public Result<Void> box(@PathVariable Long id) {
        fbaShipmentService.box(id);
        return Result.ok();
    }

    @Operation(summary = "确认发出", description = "BOXED→SHIPPED 复合事务:装箱勾稽(逐SKU=计划量,不平拦截)+ 逐SKU OUT_SHIP 动账(成本随移动加权账结转),可用不足整单回滚")
    @OperLog(module = "fulfill", action = "fba-ship")
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        fbaShipmentService.ship(id);
        return Result.ok();
    }

    @Operation(summary = "收货登记", description = "SHIPPED→RECEIVING 首登 / RECEIVING 重复登记覆盖(先删后插幂等);按SKU录平台收货量,发出SKU必须全部在列(未登记显式填0=SHORT);生成 SHORT/EXTRA/OK 差异行")
    @OperLog(module = "fulfill", action = "fba-receive")
    @PostMapping("/{id}/receive")
    public Result<Void> registerReceive(@PathVariable Long id,
                                        @Valid @RequestBody FbaReceiveRequest request) {
        fbaShipmentService.registerReceive(id, request);
        return Result.ok();
    }

    @Operation(summary = "关闭FBA单", description = "RECEIVING→CLOSED;对账差异行冻结")
    @OperLog(module = "fulfill", action = "fba-close")
    @PostMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        fbaShipmentService.close(id);
        return Result.ok();
    }

    @Operation(summary = "取消FBA单", description = "仅草稿/已装箱可取消;已发出起库存已动账禁取消(逆向=作废重开随实际使用拍板)")
    @OperLog(module = "fulfill", action = "fba-cancel")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        fbaShipmentService.cancel(id);
        return Result.ok();
    }
}
