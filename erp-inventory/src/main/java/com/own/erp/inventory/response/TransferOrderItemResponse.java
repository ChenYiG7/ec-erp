package com.own.erp.inventory.response;

import com.own.erp.inventory.entity.TransferOrderItem;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 调拨单明细对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1);from 用 builder 命名传参防相邻同类型字段错位
 */
@Builder
public record TransferOrderItemResponse(

        /** 主键 */
        Long id,

        /** 调拨单ID(transfer_order.id) */
        Long transferId,

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** 调拨数量(>0) */
        Integer quantity,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static TransferOrderItemResponse from(TransferOrderItem entity) {
        return TransferOrderItemResponse.builder()
                .id(entity.getId())
                .transferId(entity.getTransferId())
                .skuId(entity.getSkuId())
                .quantity(entity.getQuantity())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
