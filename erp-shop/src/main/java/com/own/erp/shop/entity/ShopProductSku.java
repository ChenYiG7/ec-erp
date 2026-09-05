package com.own.erp.shop.entity;

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
 * @Description : SKU映射绑定(订单/库存/财务都以内部sku_id为准)(shop_product_sku)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shop_product_sku")
public class ShopProductSku {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺商品ID(shop_product.id) */
    private Long shopProductId;

    /** 平台侧SKU标识(Amazon: seller-sku;国内: sku_id),自动匹配依据 */
    private String sellerSku;

    /** 内部SKU(product_sku.id),NULL=未绑定(进待匹配列表) */
    private Long skuId;

    /** 平台侧可售数量快照 */
    private Integer quantity;

    /** 平台侧售价快照 */
    private BigDecimal price;

    /** 币种(ISO 4217,如CNY/USD) */
    private String currency;

    /** 0待匹配(sku_id为NULL) 1商家编码自动 2人工 */
    private Integer matchStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
