package com.own.erp.purchase.request.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 采购入库单明细写侧入参(#10,随入库单创建整单提交):
 *         skuId 由服务端按采购明细回填(冗余列不收客户端值);入库仓取采购单收货仓,亦不入参
 */
@Builder
public record PurchaseInboundItemSaveRequest(

        /** 采购单明细ID(purchase_order_item.id) */
        @NotNull
        Long poItemId,

        /** 本单入库数量,须 ≤ 该明细剩余未收量(quantity - arrived_qty) */
        @NotNull
        @Positive
        Integer inboundQty
) {
}
