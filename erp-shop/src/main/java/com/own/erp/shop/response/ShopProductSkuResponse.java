package com.own.erp.shop.response;

import com.own.erp.shop.entity.ShopProductSku;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : SKU映射对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进 Response 即编译期封死
 */
@Builder
public record ShopProductSkuResponse(

        /** 主键 */
        Long id,

        /** 店铺商品ID(shop_product.id) */
        Long shopProductId,

        /** 平台侧SKU标识(Amazon: seller-sku;国内: sku_id),自动匹配依据 */
        String sellerSku,

        /** 内部SKU(product_sku.id),NULL=未绑定(进待匹配列表) */
        Long skuId,

        /** 平台侧可售数量快照 */
        Integer quantity,

        /** 平台侧售价快照 */
        BigDecimal price,

        /** 币种(ISO 4217,如CNY/USD) */
        String currency,

        /** 0待匹配(sku_id为NULL) 1商家编码自动 2人工 */
        Integer matchStatus,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static ShopProductSkuResponse from(ShopProductSku entity) {
        return ShopProductSkuResponse.builder()
                .id(entity.getId())
                .shopProductId(entity.getShopProductId())
                .sellerSku(entity.getSellerSku())
                .skuId(entity.getSkuId())
                .quantity(entity.getQuantity())
                .price(entity.getPrice())
                .currency(entity.getCurrency())
                .matchStatus(entity.getMatchStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
