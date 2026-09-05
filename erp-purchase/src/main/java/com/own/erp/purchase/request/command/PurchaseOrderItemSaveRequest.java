package com.own.erp.purchase.request.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 采购单明细写侧入参(#10,随采购单创建/更新整单提交):
 *         arrivedQty 为服务端管理列(入库核销累加),不入参;purchasePrice 可空,空按 0 计(赠品行)
 */
@Builder
public record PurchaseOrderItemSaveRequest(

        /** SKU ID(product_sku.id) */
        @NotNull
        Long skuId,

        /** 采购数量 */
        @NotNull
        @Positive
        Integer quantity,

        /** 采购单价,可空按 0 计 */
        @DecimalMin(value = "0", message = "采购单价不能为负")
        BigDecimal purchasePrice
) {
}
