package com.own.erp.order.request.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 内销订单明细写侧入参(#29 订单域补课):sku_id 必绑(内销单无平台映射翻译环节,
 *     只能选内部 SKU);小计金额服务端按 单价×数量 计算,不收客户端值(docs/07 §1 服务端管理列)
 */
@Builder
public record ManualOrderItemSaveRequest(

        /** 内部SKU ID(product_sku.id),必绑且必须存在 */
        @NotNull
        Long skuId,

        /** 商品名称快照(选填,展示用;不填落 NULL) */
        @Size(max = 255)
        String productName,

        /** 数量(≥1) */
        @NotNull
        @Min(1)
        Integer quantity,

        /** 单价(原币,≥0) */
        @NotNull
        @DecimalMin("0")
        BigDecimal unitPrice
) {
}
