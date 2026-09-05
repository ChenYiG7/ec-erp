package com.own.erp.aftersale.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 售后域常量(#12):售后类型词表、单据状态机词表(2026-09-04 拍板定版,原草案 7 态新增 RETURN_RECEIVED)。
 *         状态机:PENDING →(同意:退货/换货类)→ RETURNING →(收退件)→ RETURN_RECEIVED →(退款)→ REFUNDED →(完成)→ COMPLETED;
 *                PENDING →(同意:仅退款/补发类)→ APPROVED →(退款)→ REFUNDED;
 *                PENDING →(拒绝)→ REJECTED;CANCELLED 预留平台同步撤单(随 #12 同步 upsert)。
 *         状态字面量与 docs/sql/01_schema_init.sql 列 COMMENT、条件更新 SQL 保持同步
 */
public final class AftersaleConsts {

    /** aftersale_order.type:仅退款 */
    public static final String TYPE_REFUND_ONLY = "REFUND_ONLY";
    /** aftersale_order.type:退货退款 */
    public static final String TYPE_RETURN_REFUND = "RETURN_REFUND";
    /** aftersale_order.type:换货 */
    public static final String TYPE_EXCHANGE = "EXCHANGE";
    /** aftersale_order.type:补发 */
    public static final String TYPE_RESEND = "RESEND";

    /** aftersale_order.status:待处理 */
    public static final String STATUS_PENDING = "PENDING";
    /** aftersale_order.status:已同意(仅退款/补发类同意后落此态,可直接退款) */
    public static final String STATUS_APPROVED = "APPROVED";
    /** aftersale_order.status:待收退件(退货退款/换货类同意后落此态,等买家寄回) */
    public static final String STATUS_RETURNING = "RETURNING";
    /** aftersale_order.status:已收退件(2026-09-04 #12 拍板新增,退货类退款的强制前置) */
    public static final String STATUS_RETURN_RECEIVED = "RETURN_RECEIVED";
    /** aftersale_order.status:已退款 */
    public static final String STATUS_REFUNDED = "REFUNDED";
    /** aftersale_order.status:已完成(终态) */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** aftersale_order.status:已拒绝(终态) */
    public static final String STATUS_REJECTED = "REJECTED";
    /** aftersale_order.status:已取消(终态,预留平台同步撤单,本期无人工入口) */
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** inventory_flow.biz_type(inventory_change 命令关联业务类型):售后单退货入库(#12,同 DeliveryConsts 收口先例) */
    public static final String BIZ_TYPE_AFTERSALE_ORDER = "AFTERSALE_ORDER";

    private AftersaleConsts() {
    }
}
