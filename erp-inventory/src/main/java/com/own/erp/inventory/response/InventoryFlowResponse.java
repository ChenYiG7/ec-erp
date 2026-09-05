package com.own.erp.inventory.response;

import com.own.erp.inventory.entity.InventoryFlow;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 库存流水对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进 Response 即编译期封死
 */
@Builder
public record InventoryFlowResponse(

        /** 主键 */
        Long id,

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** 仓库ID(warehouse.id) */
        Long warehouseId,

        /** IN_PURCHASE/OUT_SHIP/IN_RETURN/ADJUST/TRANSFER_OUT/TRANSFER_IN */
        String flowType,

        /** 正负数 */
        Integer quantity,

        /** 变更前可用库存 */
        Integer beforeQty,

        /** 变更后可用库存 */
        Integer afterQty,

        /** 关联业务类型 */
        String bizType,

        /** 关联业务单据ID */
        Long bizId,

        /** 备注 */
        String remark,

        /** 操作人(sys_user.id),系统动作为NULL */
        Long createdBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static InventoryFlowResponse from(InventoryFlow entity) {
        return InventoryFlowResponse.builder()
                .id(entity.getId())
                .skuId(entity.getSkuId())
                .warehouseId(entity.getWarehouseId())
                .flowType(entity.getFlowType())
                .quantity(entity.getQuantity())
                .beforeQty(entity.getBeforeQty())
                .afterQty(entity.getAfterQty())
                .bizType(entity.getBizType())
                .bizId(entity.getBizId())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
