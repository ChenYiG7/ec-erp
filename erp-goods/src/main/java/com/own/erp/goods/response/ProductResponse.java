package com.own.erp.goods.response;

import com.own.erp.goods.entity.Product;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 商品 SPU 对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段,无敏感字段全量对外)
 */
@Builder
public record ProductResponse(

        /** 主键 */
        Long id,

        /** 内部 SPU 编码,唯一 */
        String spuCode,

        /** 商品名称 */
        String name,

        /** 分类ID(product_category.id) */
        Long categoryId,

        /** 品牌ID(brand.id) */
        Long brandId,

        /** 销售属性(JSON) */
        String attrsJson,

        /** 1=在售 0=停用 */
        Integer status,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static ProductResponse from(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .spuCode(product.getSpuCode())
                .name(product.getName())
                .categoryId(product.getCategoryId())
                .brandId(product.getBrandId())
                .attrsJson(product.getAttrsJson())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
