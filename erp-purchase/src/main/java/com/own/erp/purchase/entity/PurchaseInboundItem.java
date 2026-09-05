package com.own.erp.purchase.entity;

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
 * @Date : 2026/9/4
 * @Description : 采购入库单明细(确认入库逐行经 InventoryService.change 写 flow,#10 激活补,2026-09-04 拍板)(purchase_inbound_item)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_inbound_item")
public class PurchaseInboundItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单ID(purchase_inbound.id) */
    private Long inboundId;

    /** 采购单明细ID(purchase_order_item.id) */
    private Long poItemId;

    /** SKU ID(product_sku.id,冗余自采购明细,便于流水对账) */
    private Long skuId;

    /** 本单入库数量 */
    private Integer inboundQty;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
