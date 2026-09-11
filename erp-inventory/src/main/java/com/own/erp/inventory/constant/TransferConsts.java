package com.own.erp.inventory.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 调拨域常量(仓内作业,docs/plans/warehouse-ops.md):
 *         单据状态词表;状态字面量与 docs/sql/01_schema_init.sql transfer_order.status COMMENT
 *         及 TransferOrderMapper 条件更新 SQL 保持同步;库存流水类型词表在 erp-contract InventoryConsts
 */
public final class TransferConsts {

    /** transfer_order.status:草稿(可改可删可取消,未动账) */
    public static final String STATUS_DRAFT = "DRAFT";
    /** transfer_order.status:已确认(V1 确认即达:两腿已动账,不可改不可取消不可删) */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** transfer_order.status:已取消(终态,未动账) */
    public static final String STATUS_CANCELED = "CANCELED";

    private TransferConsts() {
    }
}
