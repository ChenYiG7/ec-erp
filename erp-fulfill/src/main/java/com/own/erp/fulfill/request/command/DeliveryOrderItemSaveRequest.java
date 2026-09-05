package com.own.erp.fulfill.request.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 发货单明细写侧入参(#11,随发货单创建整单提交):
 *         skuId 由服务端按订单明细回填(冗余列不收客户端值,未绑定 SKU 的订单行不参与发货);
 *         出库仓随发货单主表
 */
@Builder
public record DeliveryOrderItemSaveRequest(

        /** 订单明细ID(shop_order_item.id),须属于本订单且 sku_id 已绑定 */
        @NotNull
        Long orderItemId,

        /** 本单发货数量,须 ≤ 该订单明细剩余可发量 */
        @NotNull
        @Positive
        Integer shipQty
) {
}
