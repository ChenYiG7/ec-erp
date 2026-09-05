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
 * @Date : 2026/9/4
 * @Description : 发货单明细(ship 确认逐行经 InventoryService.change 写 flow,flow_type=OUT_SHIP;发货进度事实源,#11 激活补,2026-09-04 拍板:支持多次部分发货与凭证留痕)(delivery_order_item)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("delivery_order_item")
public class DeliveryOrderItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发货单ID(delivery_order.id) */
    private Long deliveryId;

    /** 订单明细ID(shop_order_item.id) */
    private Long orderItemId;

    /** SKU ID(product_sku.id,冗余自订单明细,便于流水对账;未绑定行不进发货单) */
    private Long skuId;

    /** 本单发货数量 */
    private Integer shipQty;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
