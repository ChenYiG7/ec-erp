package com.own.erp.contract;

import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 订单发货契约(#11 起):erp-fulfill 发货单建单校验/订单状态推进调用,
 *         erp-aftersale 收退件复用 findDeliveryView 做退货明细归属校验(#12,2026-09-04),
 *         实现收口 erp-api(ShopOrderApiImpl,取数走 erp-order ShopOrderService)——
 *         erp-fulfill 禁横向依赖 erp-order(铁律 2);订单状态只允许"拉单同步"与"本系统操作"
 *         两条路径产生(docs/04),发货推进 SHIPPED 属本系统操作,经 casOrderStatus 条件更新,
 *         禁旁路 update。发货进度事实源在 delivery_order_item(发货域),本契约只读订单侧
 */
public interface ShopOrderApi {

    /**
     * 订单发货视图(#11 发货建单校验/明细装配用;#12 售后收退件复用做退货明细归属校验):orderStatus/fulfillmentChannel 原样返回,
     * items 仅含 sku_id 已绑定的订单明细行(未绑定内部 SKU 的行不参与发货与发足判定,2026-09-04 拍板;
     * 售后退货同口径——未绑定行无内部 SKU 可入库,不可退);
     * 订单不存在返回 null
     */
    OrderDeliveryView findDeliveryView(Long orderId);

    /** 订单状态条件推进(WHERE order_status = fromStatus),返回是否命中;发货发足场景 WAIT_SHIP→SHIPPED */
    boolean casOrderStatus(Long orderId, String fromStatus, String toStatus);

    /**
     * 平台订单ID → 内部订单ID(shop_order.id)翻译(#12 售后同步落库:aftersale_order.order_id 存内部ID且 NOT NULL,
     * 售后单先于订单入库时返回 null,调用方跳过等下轮拉单窗口重拉);按 uk(shop_id, platform_order_id) 等值反查
     */
    Long findIdByPlatformOrderId(Long shopId, String platformOrderId);

    /**
     * 发货视图模型:@Builder 防相邻同类型参数错位(docs/07 §1 ⑤,同 InventoryChangeCommand)
     */
    @Builder
    record OrderDeliveryView(

            /** 订单ID(shop_order.id) */
            Long orderId,

            /** 店铺ID(shop.id,发货单 shop_id 服务端按此回填) */
            Long shopId,

            /** 平台订单号(shop_order.platform_order_id,#11 发货回传 2026-09-08 加字段:回传命令要素) */
            String platformOrderId,

            /** 订单状态:WAIT_PAY/WAIT_SHIP/SHIPPED/COMPLETED/CANCELLED/CLOSED */
            String orderStatus,

            /** 履约渠道:SELF_FULFILL/FBA/OVERSEAS_WAREHOUSE */
            String fulfillmentChannel,

            /** 可发行明细(sku_id 已绑定),可发数量=quantity-已发(发货域按 delivery_order_item 聚合判定) */
            List<Item> items
    ) {

        /** 订单可发行明细行 */
        @Builder
        public record Item(

                /** 订单明细ID(shop_order_item.id) */
                Long orderItemId,

                /**
                 * 平台订单行ID(shop_order_item.platform_order_item_id,#11 发货回传 2026-09-08 加字段:
                 * Amazon MFN confirmShipment 行级发运必填要素,发货明细行经本字段翻译后回传;
                 * 缺失即回传失败记 pull_log,禁静默丢行)
                 */
                String platformOrderItemId,

                /** 内部SKU ID(product_sku.id),非空 */
                Long skuId,

                /** 订单行数量(发货上限) */
                Integer quantity
        ) {
        }
    }
}
