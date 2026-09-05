package com.own.erp.shop.response;

import com.own.erp.shop.entity.ShopProduct;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺商品对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进 Response 即编译期封死
 */
@Builder
public record ShopProductResponse(

        /** 主键 */
        Long id,

        /** 店铺ID(shop.id) */
        Long shopId,

        /** 平台商品ID */
        String platformProductId,

        /** 平台SKU级商品ID(部分平台有) */
        String platformSkuId,

        /** 绑定的内部SPU,NULL=未绑定(先拉取后绑定) */
        Long productId,

        /** 平台侧listing状态快照 */
        String listingStatus,

        /** 最近同步时间 */
        LocalDateTime lastSyncAt,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static ShopProductResponse from(ShopProduct entity) {
        return ShopProductResponse.builder()
                .id(entity.getId())
                .shopId(entity.getShopId())
                .platformProductId(entity.getPlatformProductId())
                .platformSkuId(entity.getPlatformSkuId())
                .productId(entity.getProductId())
                .listingStatus(entity.getListingStatus())
                .lastSyncAt(entity.getLastSyncAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
