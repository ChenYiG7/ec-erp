package com.own.erp.shop.response;

import com.own.erp.shop.entity.Shop;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺对外结构(docs/07 §1):Response 只声明允许对外的字段——
 *     appSecret/refreshToken 根本不建字段(编译期封死,漏不出去);accessToken 只回 Service 读出口脱敏后的掩码值。
 *     实体 Shop 仅存活于 Mapper/Service 层,禁止从 Controller 直接返回
 */
@Builder
public record ShopResponse(

        /** 主键 */
        Long id,

        /** 商户ID(多商户预留,一期固定 1) */
        Long merchantId,

        /** 平台编码 = PlatformType.name(),如 TAOBAO / AMAZON / SHOPEE */
        String platform,

        /** 店铺名称 */
        String shopName,

        /** 平台侧卖家/店铺标识(Amazon: SellerId;国内平台: 店铺 sid) */
        String sellerId,

        /** 平台应用Key,明文存(非机密,参与签名) */
        String appKey,

        /** 令牌掩码(密文前 6 位 + ***,脱敏在 ShopService 读出口统一处理;真实密文/明文永不外传) */
        String accessToken,

        /** 令牌过期时间 */
        LocalDateTime tokenExpireAt,

        /** 1=启用 0=停用 */
        Integer status,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);凭证脱敏已由 Service mask() 完成 */
    public static ShopResponse from(Shop shop) {
        return ShopResponse.builder()
                .id(shop.getId())
                .merchantId(shop.getMerchantId())
                .platform(shop.getPlatform())
                .shopName(shop.getShopName())
                .sellerId(shop.getSellerId())
                .appKey(shop.getAppKey())
                .accessToken(shop.getAccessToken())
                .tokenExpireAt(shop.getTokenExpireAt())
                .status(shop.getStatus())
                .createdAt(shop.getCreatedAt())
                .updatedAt(shop.getUpdatedAt())
                .build();
    }
}
