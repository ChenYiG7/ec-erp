package com.own.erp.contract;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 库存变更命令(契约参数模型,与 InventoryFlow 实体解耦——契约模块不引业务实体)。
 *         @Builder 防调用方相邻同类型参数错位(docs/07 §1 ⑤,skuId/warehouseId 双 Long 相邻);
 *         语义与校验同 InventoryService.change:quantity 正负数、after<0 拒绝、同事务写 inventory_flow
 */
@Builder
public record InventoryChangeCommand(

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** 仓库ID(warehouse.id) */
        Long warehouseId,

        /** 变动数量,正负数(IN_PURCHASE/IN_RETURN/IN_TRANSIT/LOCK_SHIP 为正,OUT_SHIP/TRANSFER_OUT 为负,
         *  ADJUST/IN_TRANSIT/LOCK_SHIP 可正可负;列语义矩阵见 InventoryConsts 各值注释) */
        Integer quantity,

        /** 流水类型,取 InventoryConsts.FLOW_TYPE_* */
        String flowType,

        /** 关联业务类型(如 PURCHASE_INBOUND,由调用方业务域定义) */
        String bizType,

        /** 关联业务单据ID(如入库单ID) */
        Long bizId,

        /** 备注 */
        String remark,

        /** 操作人(sys_user.id),系统动作为 null */
        Long createdBy
) {
}
