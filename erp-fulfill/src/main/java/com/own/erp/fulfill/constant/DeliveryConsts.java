package com.own.erp.fulfill.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 发货域常量(#11):单据状态词表、库存流水关联业务类型、订单侧前置状态字面量。
 *         状态字面量与 docs/sql/01_schema_init.sql 列 COMMENT、条件更新 SQL 保持同步;
 *         库存流水类型词表在 erp-contract InventoryConsts(随契约走),OUT_SHIP 为发货出库专用
 */
public final class DeliveryConsts {

    /** delivery_order.status:待发货(可改可删可取消) */
    public static final String DELIVERY_PENDING = "PENDING";
    /** delivery_order.status:已发货(库存已动账,禁删改) */
    public static final String DELIVERY_SHIPPED = "SHIPPED";
    /** delivery_order.status:已签收(终态) */
    public static final String DELIVERY_DELIVERED = "DELIVERED";
    /** delivery_order.status:已取消(终态,不占订单可发量) */
    public static final String DELIVERY_CANCELLED = "CANCELLED";

    /** inventory_flow.biz_type:销售发货出库(关联单据 = delivery_order.id) */
    public static final String BIZ_TYPE_DELIVERY_ORDER = "DELIVERY_ORDER";

    /** shop_order.order_status:待发货(建发货单前置状态;发足后经 casOrderStatus 推进到此) */
    public static final String ORDER_WAIT_SHIP = "WAIT_SHIP";
    /** shop_order.order_status:已发货 */
    public static final String ORDER_SHIPPED = "SHIPPED";
    /** shop_order.fulfillment_channel:卖家自发货(仅此渠道产生系统内发货单,FBA/海外仓平台履约,docs/03) */
    public static final String CHANNEL_SELF_FULFILL = "SELF_FULFILL";

    private DeliveryConsts() {
    }
}
