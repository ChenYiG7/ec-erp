package com.own.erp.purchase.response;

import com.own.erp.purchase.entity.PurchaseOrderItem;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 采购单明细对外结构(#10,随采购单详情带出;poId 由父级持有不重复出)
 */
@Builder
public record PurchaseOrderItemResponse(

        /** 主键 */
        Long id,

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** 采购数量 */
        Integer quantity,

        /** 已入库数量(入库核销累加) */
        Integer arrivedQty,

        /** 采购单价 */
        BigDecimal purchasePrice
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static PurchaseOrderItemResponse from(PurchaseOrderItem entity) {
        return PurchaseOrderItemResponse.builder()
                .id(entity.getId())
                .skuId(entity.getSkuId())
                .quantity(entity.getQuantity())
                .arrivedQty(entity.getArrivedQty())
                .purchasePrice(entity.getPurchasePrice())
                .build();
    }
}
