package com.own.erp.aftersale.response;

import com.own.erp.aftersale.entity.AftersaleReturnItem;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 售后退货明细对外结构(#12 收退件实收明细,随售后单详情返回;docs/07 §1 record+@Builder)
 */
@Builder
public record AftersaleReturnItemResponse(

        /** 主键 */
        Long id,

        /** 订单明细ID(shop_order_item.id,SKU 归属锚点) */
        Long orderItemId,

        /** 内部SKU ID(product_sku.id) */
        Long skuId,

        /** 实收退货数量(正数) */
        Integer returnQty
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static AftersaleReturnItemResponse from(AftersaleReturnItem entity) {
        return AftersaleReturnItemResponse.builder()
                .id(entity.getId())
                .orderItemId(entity.getOrderItemId())
                .skuId(entity.getSkuId())
                .returnQty(entity.getReturnQty())
                .build();
    }
}
