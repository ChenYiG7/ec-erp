package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 订单审核域词表(#29 订单域补课):订单来源 + 审核状态第二状态机,随契约走
 *         (跨域消费方 erp-fulfill 建发货单前置校验需读审核态,铁律 2 禁横向依赖)。
 *         review_status 独立于 order_status:拉单会推进 order_status,审核只由人工动作推进,
 *         两状态机不合并(计划书 §六红线);字面量与 docs/sql/01_schema_init.sql 列 COMMENT 同步。
 *         REJECTED 单在建发货单时被拦;复核(3→2)走同一审核端点,故 3 不在终态,2 为审核终态
 */
public final class OrderReviewConsts {

    /** shop_order.order_source:平台拉单落库 */
    public static final String SOURCE_PLATFORM = "PLATFORM";
    /** shop_order.order_source:内销手工录单(合成单号 MAN-*) */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** shop_order.review_status:无需审核(默认,风控规则未命中) */
    public static final int REVIEW_NONE = 0;
    /** shop_order.review_status:待审核(风控命中,建发货单被拦) */
    public static final int REVIEW_PENDING = 1;
    /** shop_order.review_status:已通过(可建发货单,审核终态) */
    public static final int REVIEW_APPROVED = 2;
    /** shop_order.review_status:已驳回(建发货单被拦,可由人工复核改判) */
    public static final int REVIEW_REJECTED = 3;

    private OrderReviewConsts() {
    }
}
