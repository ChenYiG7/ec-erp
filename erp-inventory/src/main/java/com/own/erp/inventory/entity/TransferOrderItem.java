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
 * @Date : 2026/9/11
 * @Description : 调拨单明细(确认时逐行经 InventoryService.transfer 写 TRANSFER_OUT/IN 两腿)(transfer_order_item)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("transfer_order_item")
public class TransferOrderItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调拨单ID(transfer_order.id) */
    private Long transferId;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 调拨数量(>0) */
    private Integer quantity;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
