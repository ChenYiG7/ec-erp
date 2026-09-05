package com.own.erp.order.response;

import com.own.erp.order.entity.ShopOrderItem;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 平台订单明细对外结构(docs/07 §1):随 ShopOrderResponse.items 返回,不单出接口
 */
@Builder
public record ShopOrderItemResponse(

        /** 主键 */
        Long id,

        /** 订单ID(shop_order.id) */
        Long orderId,

        /** 平台子订单/明细ID */
        String platformOrderItemId,

        /** SKU映射ID(shop_product_sku.id) */
        Long shopProductSkuId,

        /** 落库时匹配到的内部SKU */
        Long skuId,

        /** 平台侧SKU标识(seller_sku快照) */
        String platformSku,

        /** 商品名称快照 */
        String productName,

        /** 数量 */
        Integer quantity,

        /** 单价(原币) */
        BigDecimal unitPrice,

        /** 小计金额(原币)=单价×数量 */
        BigDecimal itemAmount,

        /** 币种(ISO 4217) */
        String currency
) {

    /** 实体 → Response 显式逐字段映射(shop_product_id 为内部关联列,不对外) */
    public static ShopOrderItemResponse from(ShopOrderItem entity) {
        return ShopOrderItemResponse.builder()
                .id(entity.getId())
                .orderId(entity.getOrderId())
                .platformOrderItemId(entity.getPlatformOrderItemId())
                .shopProductSkuId(entity.getShopProductSkuId())
                .skuId(entity.getSkuId())
                .platformSku(entity.getPlatformSku())
                .productName(entity.getProductName())
                .quantity(entity.getQuantity())
                .unitPrice(entity.getUnitPrice())
                .itemAmount(entity.getItemAmount())
                .currency(entity.getCurrency())
                .build();
    }
}
