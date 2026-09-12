package com.own.erp.finance.request.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 手工资金登记入参(#31,docs/plans/payment-receipt.md §2.3):
 *     派生遗漏/非结算回款(其他打款)等走本入口,biz_type 服务端固定 MANUAL_ADJUST,不分摊;
 *     非 CNY 按 paidAt 回溯 resolveRate 冻结汇率,缺报价 amountCny 留 NULL(查询面缺口计数,禁猜)。
 *     财务写操作限 admin(Controller @PreAuthorize 双闸)
 */
@Builder
public record ManualPaymentRequest(

        /** 资金方向:EXPENSE付款/INCOME回款(必填,词表服务端校验) */
        @NotNull
        String direction,

        /** 往来方类型:SUPPLIER/PLATFORM/OTHER(必填,词表服务端校验) */
        @NotNull
        String partyType,

        /** 往来方ID(supplier.id/shop.id;OTHER 可空,其余必填) */
        Long partyId,

        /** 原币金额(恒正) */
        @NotNull
        @DecimalMin(value = "0.0001", message = "金额必须大于0")
        BigDecimal amount,

        /** 币种(ISO 4217,空按 CNY) */
        @Size(max = 3)
        String currency,

        /** 收付款时间(空=服务端当前时间,亦为汇率回溯锚点) */
        LocalDateTime paidAt,

        /** 结算方式(银行转账/支付宝/平台打款等,走 sys_dict) */
        @Size(max = 32)
        String method,

        /** 备注 */
        @Size(max = 255)
        String remark
) {
}
