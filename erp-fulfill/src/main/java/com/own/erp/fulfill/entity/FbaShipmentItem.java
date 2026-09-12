package com.own.erp.fulfill.entity;

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
 * @Date : 2026/9/12
 * @Description : FBA发货单计划行(SKU清单,SHIPPED 装箱勾稽基准;明细域随主单物理删,改单先删后插)(fba_shipment_item)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("fba_shipment_item")
public class FbaShipmentItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** FBA发货单ID(fba_shipment.id) */
    private Long shipmentId;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 计划发货数量(大于0;同单同SKU唯一合并为一行) */
    private Integer planQty;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
