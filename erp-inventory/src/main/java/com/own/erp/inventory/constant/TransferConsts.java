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
    /** transfer_order.status:在途(已发未达,#30 余量① 在途模式:confirm 落 OUT+占调入仓在途,不可改不可取消不可删) */
    public static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
    /** transfer_order.status:已确认(调拨已达:DIRECT 确认即达两腿动账 / IN_TRANSIT 到货核销后;不可改不可取消不可删) */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** transfer_order.status:已取消(终态,未动账) */
    public static final String STATUS_CANCELED = "CANCELED";

    /** transfer_order.transit_mode:确认即达(V1 默认,confirm 两腿直达) */
    public static final String MODE_DIRECT = "DIRECT";
    /** transfer_order.transit_mode:在途(confirm 调出仓 OUT+调入仓占在途,receive 到货核销,#30 余量①) */
    public static final String MODE_IN_TRANSIT = "IN_TRANSIT";

    private TransferConsts() {
    }
}
