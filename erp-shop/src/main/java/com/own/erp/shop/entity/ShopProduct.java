package com.own.erp.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 店铺商品(平台listing ↔ 内部SPU)(shop_product)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shop_product")
public class ShopProduct {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID(shop.id) */
    private Long shopId;

    /** 平台商品ID */
    private String platformProductId;

    /** 平台SKU级商品ID(部分平台有) */
    private String platformSkuId;

    /** 绑定的内部SPU,NULL=未绑定(先拉取后绑定) */
    private Long productId;

    /** 平台侧listing状态快照 */
    private String listingStatus;

    /** 最近同步时间 */
    private LocalDateTime lastSyncAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
