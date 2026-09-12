package com.own.erp.finance.request.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 采购付款登记入参(#31 收付款/回款,docs/plans/payment-receipt.md §2.2):
 *     一笔付款(EXPENSE/PURCHASE_PAYMENT,强制 CNY 与本位币采购总额勾稽)+ 采购单分摊行(一付多单)。
 *     服务端管理列均不入参:流水号/方向/业务类型/往来方(取采购单供应商)/汇率(=1)/状态(NORMAL)/创建人;
 *     paidAt 空 = 服务端按当前时间落账
 */
@Builder
public record PurchasePaymentRequest(

        /** 付款金额(CNY,恒正;必须 ≥ Σ 分摊金额,允许部分挂账) */
        @NotNull
        @DecimalMin(value = "0.0001", message = "付款金额必须大于0")
        BigDecimal amount,

        /** 收付款时间(空=服务端当前时间) */
        LocalDateTime paidAt,

        /** 结算方式(银行转账/支付宝等,走 sys_dict) */
        @Size(max = 32)
        String method,

        /** 备注 */
        @Size(max = 255)
        String remark,

        /** 采购单分摊行(至少一行;同采购单不可重复;每行金额恒正) */
        @NotEmpty
        @Valid
        List<Alloc> allocs
) {

    /** 分摊行:采购单ID + 本次分摊金额(CNY) */
    @Builder
    public record Alloc(

            /** 采购单ID(purchase_order.id;必须已审核且未超额) */
            @NotNull
            Long poId,

            /** 分摊金额(恒正;按单"已付+本次 ≤ 采购总额",超额拦截) */
            @NotNull
            @DecimalMin(value = "0.0001", message = "分摊金额必须大于0")
            BigDecimal amount
    ) {
    }
}
