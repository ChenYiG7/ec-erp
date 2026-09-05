package com.own.erp.fulfill.response;

import com.own.erp.fulfill.entity.DeliveryOrderItem;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 发货单明细对外结构(#11,详情随单带回,列表不带)
 */
@Builder
public record DeliveryOrderItemResponse(

        /** 主键 */
        Long id,

        /** 发货单ID(delivery_order.id) */
        Long deliveryId,

        /** 订单明细ID(shop_order_item.id) */
        Long orderItemId,

        /** SKU ID(product_sku.id,冗余自订单明细) */
        Long skuId,

        /** 本单发货数量 */
        Integer shipQty,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static DeliveryOrderItemResponse from(DeliveryOrderItem entity) {
        return DeliveryOrderItemResponse.builder()
                .id(entity.getId())
                .deliveryId(entity.getDeliveryId())
                .orderItemId(entity.getOrderItemId())
                .skuId(entity.getSkuId())
                .shipQty(entity.getShipQty())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
