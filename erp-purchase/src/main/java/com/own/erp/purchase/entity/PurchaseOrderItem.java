package com.own.erp.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 采购单明细(purchase_order_item)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_order_item")
public class PurchaseOrderItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采购单ID(purchase_order.id) */
    private Long poId;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 采购数量 */
    private Integer quantity;

    /** 已入库数量(入库核销累加) */
    private Integer arrivedQty;

    /** 采购单价 */
    private BigDecimal purchasePrice;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
