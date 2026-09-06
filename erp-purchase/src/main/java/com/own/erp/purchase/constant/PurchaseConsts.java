package com.own.erp.purchase.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 采购域常量(#10):单据状态词表与库存流水关联业务类型。
 *         状态字面量与 docs/sql/01_schema_init.sql 列 COMMENT、条件更新 SQL(@Update 字面量)保持同步;
 *         库存流水类型词表在 erp-contract InventoryConsts(随契约走)
 */
public final class PurchaseConsts {

    /** purchase_order.status:草稿(可改可删) */
    public static final String PO_DRAFT = "DRAFT";
    /** purchase_order.status:已审核(可入库) */
    public static final String PO_AUDITED = "AUDITED";
    /** purchase_order.status:部分入库 */
    public static final String PO_PARTIAL_RECEIVED = "PARTIAL_RECEIVED";
    /** purchase_order.status:已入库(全部明细收齐) */
    public static final String PO_RECEIVED = "RECEIVED";
    /** purchase_order.status:已关闭(终态,剩余量作废) */
    public static final String PO_CLOSED = "CLOSED";

    /** purchase_inbound.status:待入库(可改可删可取消) */
    public static final String INBOUND_PENDING = "PENDING";
    /** purchase_inbound.status:已入库(核销完成,不可删改) */
    public static final String INBOUND_RECEIVED = "RECEIVED";
    /** purchase_inbound.status:已取消(终态) */
    public static final String INBOUND_CANCELLED = "CANCELLED";

    /** inventory_flow.biz_type:采购入库核销(关联单据 = purchase_inbound.id) */
    public static final String BIZ_TYPE_PURCHASE_INBOUND = "PURCHASE_INBOUND";

    /** inventory_flow.biz_type:采购审核占在途/关闭释放(关联单据 = purchase_order.id;#7 2026-09-06) */
    public static final String BIZ_TYPE_PURCHASE_ORDER = "PURCHASE_ORDER";

    private PurchaseConsts() {
    }
}
