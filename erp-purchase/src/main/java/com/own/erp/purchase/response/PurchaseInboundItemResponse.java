package com.own.erp.purchase.response;

import com.own.erp.purchase.entity.PurchaseInboundItem;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 采购入库单明细对外结构(#10,随入库单详情带出;inboundId 由父级持有不重复出)
 */
@Builder
public record PurchaseInboundItemResponse(

        /** 主键 */
        Long id,

        /** 采购单明细ID(purchase_order_item.id) */
        Long poItemId,

        /** SKU ID(product_sku.id,冗余自采购明细) */
        Long skuId,

        /** 本单入库数量 */
        Integer inboundQty
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static PurchaseInboundItemResponse from(PurchaseInboundItem entity) {
        return PurchaseInboundItemResponse.builder()
                .id(entity.getId())
                .poItemId(entity.getPoItemId())
                .skuId(entity.getSkuId())
                .inboundQty(entity.getInboundQty())
                .build();
    }
}
