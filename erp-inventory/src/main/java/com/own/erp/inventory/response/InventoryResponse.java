package com.own.erp.inventory.response;

import com.own.erp.inventory.entity.Inventory;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 库存对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进 Response 即编译期封死
 */
@Builder
public record InventoryResponse(

        /** 主键 */
        Long id,

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** 仓库ID(warehouse.id) */
        Long warehouseId,

        /** 在库 */
        Integer qtyOnHand,

        /** 占用(已分配未发货) */
        Integer qtyLocked,

        /** 在途(采购未入库) */
        Integer qtyTransit,

        /** 可用=在库-占用,由InventoryService同事务维护,禁止旁路update */
        Integer qtyAvailable,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static InventoryResponse from(Inventory entity) {
        return InventoryResponse.builder()
                .id(entity.getId())
                .skuId(entity.getSkuId())
                .warehouseId(entity.getWarehouseId())
                .qtyOnHand(entity.getQtyOnHand())
                .qtyLocked(entity.getQtyLocked())
                .qtyTransit(entity.getQtyTransit())
                .qtyAvailable(entity.getQtyAvailable())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
