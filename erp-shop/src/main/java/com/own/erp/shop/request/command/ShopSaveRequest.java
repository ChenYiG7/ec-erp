package com.own.erp.shop.request.command;

import com.own.erp.shop.entity.Shop;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)。
 *     刻意不含:id/createdAt/updatedAt(服务端管理)、merchantId(一期服务端固定 1,防 mass assignment)。
 *     凭证三字段语义(docs/07 §7):null/空串=不修改,掩码回传=忽略并记日志,真实新值才加密覆盖
 */
@Builder
public record ShopSaveRequest(

        /** 平台编码 = PlatformType.name(),如 TAOBAO / AMAZON / SHOPEE */
        @NotBlank(message = "平台编码不能为空")
        String platform,

        /** 店铺名称 */
        String shopName,

        /** 平台侧卖家/店铺标识(Amazon: SellerId;国内平台: 店铺 sid) */
        String sellerId,

        /** 平台应用Key,明文存(非机密,参与签名) */
        String appKey,

        /** 平台应用密钥,明文入参,AES-GCM 加密落库;null/空串=不修改 */
        String appSecret,

        /** 访问令牌,明文入参,AES-GCM 加密落库;null/空串=不修改,掩码回传=忽略 */
        String accessToken,

        /** 刷新令牌,明文入参,AES-GCM 加密落库;null/空串=不修改,掩码回传=忽略 */
        String refreshToken,

        /** 令牌过期时间 */
        LocalDateTime tokenExpireAt,

        /** 1=启用 0=停用 */
        Integer status
) {

    /** 手写 toString 脱敏(docs/07 §1,与实体凭证 @ToString.Exclude 同源红线):record 自动 toString 携带全部组件,凭证三字段禁入日志 */
    @Override
    public String toString() {
        return "ShopSaveRequest[platform=" + platform + ", shopName=" + shopName + ", sellerId=" + sellerId
                + ", appKey=" + appKey + ", appSecret=****, accessToken=****, refreshToken=****"
                + ", tokenExpireAt=" + tokenExpireAt + ", status=" + status + "]";
    }

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);凭证原文直传,加密只在 ShopService 唯一入口做 */
    public Shop toEntity() {
        Shop shop = new Shop();
        shop.setPlatform(platform);
        shop.setShopName(shopName);
        shop.setSellerId(sellerId);
        shop.setAppKey(appKey);
        shop.setAppSecret(appSecret);
        shop.setAccessToken(accessToken);
        shop.setRefreshToken(refreshToken);
        shop.setTokenExpireAt(tokenExpireAt);
        shop.setStatus(status);
        return shop;
    }
}
