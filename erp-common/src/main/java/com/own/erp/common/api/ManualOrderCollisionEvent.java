package com.own.erp.common.api;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 内销订单合成单号冲突事件(#29 订单域补课):拉单写口 saveUnifiedOrder 按 uk
 *         (shop_id, platform_order_id) 命中已有 MANUAL 单时发布——理论上不可能(合成号 MAN-* 与平台
 *         单号不同源),纯防御位:拒绝覆盖并告警。
 *         事件只进 erp-common(发布方 erp-order 与监听方 erp-api 互不依赖,铁律 2,同
 *         DeliveryShippedEvent/SystemConfigChangedEvent 口径);erp-api 监听后经
 *         SysNotificationService 扇出站内通知并落 pull_log 痕迹
 */
public record ManualOrderCollisionEvent(

        /** 店铺ID(shop.id) */
        Long shopId,

        /** 平台订单号(与内销合成单号相撞的拉单侧单号) */
        String platformOrderId,

        /** 已存在的内销单主键(shop_order.id,未被覆盖) */
        Long orderId
) {
}
