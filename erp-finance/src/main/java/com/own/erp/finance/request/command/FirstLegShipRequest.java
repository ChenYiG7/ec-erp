package com.own.erp.finance.request.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程发货单「确认发货」入参(#33):BOXED→SHIPPED 同动作录运费并冻结汇率。
 *     汇率口径:exchangeRate 手填优先(必须大于 0);空 = 按 shippedAt 回溯 resolveRate,
 *     非 CNY 无报价直接拦截(禁猜),补汇率快照或手填后重试;CNY 短路为 1
 */
@Builder
public record FirstLegShipRequest(

        /** 物流商(可空) */
        String carrier,

        /** 运单号(可空) */
        String waybillNo,

        /** 计费重 kg(可空,非负) */
        @DecimalMin(value = "0", message = "计费重不能为负")
        BigDecimal chargeWeight,

        /** 体积重 kg(可空,非负) */
        @DecimalMin(value = "0", message = "体积重不能为负")
        BigDecimal volumeWeight,

        /** 头程运费原币(必填,大于 0) */
        @NotNull(message = "运费金额必填")
        @DecimalMin(value = "0.0001", message = "运费必须大于0")
        BigDecimal freightAmount,

        /** 运费币种(ISO 4217,空=CNY) */
        String currency,

        /** 手填折算汇率(1 currency=rate CNY);空=按发货日回溯报价,无报价拦截 */
        @DecimalMin(value = "0.00000001", message = "汇率必须大于0")
        BigDecimal exchangeRate,

        /** 发货时间(空=当前时间;汇率回溯锚点) */
        LocalDateTime shippedAt

) {
}
