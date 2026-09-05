package com.own.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 库存(只能经 inventory_flow 变动)(inventory)。
 *     @Builder 仅用于 change() 新行一次成型(docs/07 §1 分级⑤);存量行是读改写生命周期,只走 setter(白名单④)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("inventory")
public class Inventory {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 仓库ID(warehouse.id) */
    private Long warehouseId;

    /** 在库 */
    private Integer qtyOnHand;

    /** 占用(已分配未发货) */
    private Integer qtyLocked;

    /** 在途(采购未入库) */
    private Integer qtyTransit;

    /** 可用=在库-占用,由InventoryService同事务维护,禁止旁路update */
    private Integer qtyAvailable;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
