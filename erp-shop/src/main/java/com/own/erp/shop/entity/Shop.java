package com.own.erp.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺(shop):多平台店铺档案 + 授权凭证。
 *     appSecret/accessToken/refreshToken 为 AES-GCM 密文落库(密钥 ERP_TOKEN_KEY),
 *     加解密唯一入口 {@link com.own.erp.shop.security.CryptoService},经 ShopService 写入,禁止旁路
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户ID(多商户预留,一期固定 1) */
    private Long merchantId;

    /** 平台编码 = PlatformType.name(),如 TAOBAO / AMAZON / SHOPEE */
    private String platform;

    /** 店铺名称 */
    private String shopName;

    /** 平台侧卖家/店铺标识(Amazon: SellerId;国内平台: 店铺 sid) */
    private String sellerId;

    /** 平台应用Key,明文存(非机密,参与签名) */
    private String appKey;

    /** AES-GCM 密文;toString 排除,防日志意外泄漏 */
    @ToString.Exclude
    private String appSecret;

    /** AES-GCM 密文;toString 排除,防日志意外泄漏 */
    @ToString.Exclude
    private String accessToken;

    /** AES-GCM 密文;toString 排除,防日志意外泄漏 */
    @ToString.Exclude
    private String refreshToken;

    /** 令牌过期时间 */
    private LocalDateTime tokenExpireAt;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id);delval=id 配合唯一键含 deleted,删后同键可重建(TODO#7) */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
