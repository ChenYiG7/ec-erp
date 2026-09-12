package com.own.erp.finance.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.api.Result;
import com.own.erp.finance.request.command.ManualPaymentRequest;
import com.own.erp.finance.request.command.PurchasePaymentRequest;
import com.own.erp.finance.request.query.PaymentRecordQuery;
import com.own.erp.finance.response.PaymentDetailResponse;
import com.own.erp.finance.response.PaymentRecordResponse;
import com.own.erp.finance.response.PaymentSummaryResponse;
import com.own.erp.finance.response.PlatformReceiptRow;
import com.own.erp.finance.response.PurchasePaidRow;
import com.own.erp.finance.response.SupplierPayableRow;
import com.own.erp.finance.service.PaymentRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水接口(#31 收付款/回款):统一资金账查询面 + 三个写入口,一律走 PaymentRecordService
 *     (docs/07 §2.1 Controller 不直连 Mapper);API 模型 = query/command 分包入参 + response 出参,entity 不出 Service。
 *     读侧登录即可;财务写操作(登记/作废)admin 双闸——@PreAuthorize hasRole('admin') + 菜单 perm_key 前端收口
 *     (同汇率快照口径);结算回款派生无 HTTP 入口,由 SettlementService 落报告同事务调用
 */
@Tag(name = "资金流水", description = "收付款/回款统一账:流水查询、采购付款登记、手工登记、作废与资金流汇总")
@RestController
@RequestMapping("/api/finance/payments")
@RequiredArgsConstructor
public class PaymentRecordController {

    private final PaymentRecordService paymentRecordService;

    @Operation(summary = "流水分页", description = "方向/类型/往来方/期间过滤;默认滤 VOIDED,includeVoided=true 为对账口径")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<Page<PaymentRecordResponse>> page(PaymentRecordQuery query) {
        return Result.ok(paymentRecordService.page(query));
    }

    @Operation(summary = "流水详情", description = "带采购分摊行;不存在返回 null data")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public Result<PaymentDetailResponse> get(@PathVariable Long id) {
        return Result.ok(paymentRecordService.getDetail(id));
    }

    @Operation(summary = "采购付款登记", description = "一笔付款分摊到多张采购单;未审核不可付,超额分摊拦截;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/purchase")
    public Result<Long> registerPurchase(@Valid @RequestBody PurchasePaymentRequest request) {
        return Result.ok(paymentRecordService.registerPurchasePayment(request));
    }

    @Operation(summary = "手工资金登记", description = "补录非结算回款/其他收付款,非CNY按收付款日回溯汇率;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/manual")
    public Result<Long> registerManual(@Valid @RequestBody ManualPaymentRequest request) {
        return Result.ok(paymentRecordService.registerManual(request));
    }

    @Operation(summary = "作废流水", description = "NORMAL→VOIDED 留痕禁物理删;分摊随作废失效,采购待付自动还原;限 admin")
    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id}/void")
    public Result<Void> voidPayment(@PathVariable Long id) {
        paymentRecordService.voidPayment(id);
        return Result.ok();
    }

    @Operation(summary = "采购单已付批量", description = "采购列表/详情资金视图按 poIds 批量取 ΣNORMAL 分摊,无分摊为 0")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/purchase-paid")
    public Result<List<PurchasePaidRow>> purchasePaid(@RequestBody List<Long> poIds) {
        return Result.ok(paymentRecordService.listPurchasePaid(poIds));
    }

    @Operation(summary = "供应商应付视图", description = "应付(非草稿采购单总额)/已付(ΣNORMAL分摊)/待付分页")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/parties/suppliers")
    public Result<Page<SupplierPayableRow>> supplierParties(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(paymentRecordService.pageSupplierPayables(pageNo, pageSize));
    }

    @Operation(summary = "平台回款视图", description = "按店铺聚合期间回款,跨币种看 CNY 列,缺汇率行计数不静默")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/parties/platforms")
    public Result<List<PlatformReceiptRow>> platformParties(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime paidFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime paidTo) {
        return Result.ok(paymentRecordService.listPlatformReceipts(paidFrom, paidTo));
    }

    @Operation(summary = "期间资金汇总", description = "NORMAL 流水收付 CNY 净额 + 缺汇率行数")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/summary")
    public Result<PaymentSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime paidFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime paidTo) {
        return Result.ok(paymentRecordService.summary(paidFrom, paidTo));
    }
}
