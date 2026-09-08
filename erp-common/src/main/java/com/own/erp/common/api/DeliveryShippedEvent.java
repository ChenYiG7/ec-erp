package com.own.erp.common.api;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 发货单已确认发货事件(#11 发货回传平台编排接线):DeliveryOrderService.ship 事务内发布,
 *         监听方 erp-api ShipmentSyncService 以 AFTER_COMMIT 相位消费——**事务提交后才回传平台**,
 *         回传失败(网络/平台侧/凭证)只记 pull_log 不回滚本地已发货状态(平台侧可手工补,docs/04 回传拍板)。
 *         事件只进 erp-common:发布方 erp-fulfill 与监听方 erp-api 互不依赖(铁律 2 同款思路,
 *         同 #18 SystemConfigChangedEvent 先例);erp-fulfill 不具备 ShopSession/AdapterRegistry,
 *         回传编排只可能落在 erp-api。
 *         携带 orderId/shopId 供监听方免查主单直接裁剪(FBA/海外仓不回传、adapter 未接入跳过)
 */
public record DeliveryShippedEvent(

        /** 发货单ID(delivery_order.id) */
        Long deliveryId,

        /** 订单ID(shop_order.id) */
        Long orderId,

        /** 店铺ID(shop.id) */
        Long shopId
) {
}
