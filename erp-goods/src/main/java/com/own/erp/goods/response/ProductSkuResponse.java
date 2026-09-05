package com.own.erp.goods.response;

import com.own.erp.goods.entity.ProductSku;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品 SKU 对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段,无敏感字段全量对外)
 */
@Builder
public record ProductSkuResponse(

        /** 主键 */
        Long id,

        /** 所属SPU(product.id) */
        Long productId,

        /** 内部 SKU 编码,唯一——各平台 seller_sku 通过 shop_product_sku 映射到它 */
        String skuCode,

        /** 条形码(EAN/UPC) */
        String barcode,

        /** 规格值(JSON) */
        String attrsJson,

        /** 成本价 DECIMAL(12,4) */
        BigDecimal costPrice,

        /** 重量(g) */
        Integer weightG,

        /** 海关 HS 编码 */
        String hsCode,

        /** 申报价值(币种随订单 currency) */
        BigDecimal declaredValue,

        /** 是否含电池 1/0 */
        Integer battery,

        /** 1=启用 0=禁用 */
        Integer status,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static ProductSkuResponse from(ProductSku sku) {
        return ProductSkuResponse.builder()
                .id(sku.getId())
                .productId(sku.getProductId())
                .skuCode(sku.getSkuCode())
                .barcode(sku.getBarcode())
                .attrsJson(sku.getAttrsJson())
                .costPrice(sku.getCostPrice())
                .weightG(sku.getWeightG())
                .hsCode(sku.getHsCode())
                .declaredValue(sku.getDeclaredValue())
                .battery(sku.getBattery())
                .status(sku.getStatus())
                .createdAt(sku.getCreatedAt())
                .updatedAt(sku.getUpdatedAt())
                .build();
    }
}
