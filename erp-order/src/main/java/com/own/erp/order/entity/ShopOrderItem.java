package com.own.erp.order.entity;

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
 * @Date : 2026/9/3
 * @Description : 订单明细(shop_order_item)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shop_order_item")
public class ShopOrderItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID(shop_order.id) */
    private Long orderId;

    /** 平台子订单/明细ID */
    private String platformOrderItemId;

    /** 店铺商品ID(shop_product.id) */
    private Long shopProductId;

    /** SKU映射ID(shop_product_sku.id) */
    private Long shopProductSkuId;

    /** 落库时匹配到的内部SKU */
    private Long skuId;

    /** 平台侧SKU标识(seller_sku快照) */
    private String platformSku;

    /** 商品名称快照 */
    private String productName;

    /** 数量 */
    private Integer quantity;

    /** 单价(原币) */
    private BigDecimal unitPrice;

    /** 小计金额(原币)=单价×数量 */
    private BigDecimal itemAmount;

    /** 币种(ISO 4217) */
    private String currency;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
