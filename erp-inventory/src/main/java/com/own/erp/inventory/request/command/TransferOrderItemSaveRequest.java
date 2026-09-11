package com.own.erp.inventory.request.command;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 调拨单明细写侧入参(嵌套于 TransferOrderSaveRequest.items;skuId 服务端校验存在性,docs/07 §1 record+@Builder)
 */
@Builder
public record TransferOrderItemSaveRequest(

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** 调拨数量(> 0) */
        Integer quantity

) {
}
