package com.own.erp.inventory.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点域常量(仓内作业,docs/plans/warehouse-ops.md):
 *         单据状态词表与盘点范围词表;状态字面量与 docs/sql/01_schema_init.sql stocktake_order.status COMMENT
 *         及 StocktakeOrderMapper 条件更新 SQL 保持同步;库存流水类型词表在 erp-contract InventoryConsts(随契约走)
 */
public final class StocktakeConsts {

    /** stocktake_order.status:草稿(建单已完成账面快照,未开始录实盘;可改可删可取消) */
    public static final String STATUS_DRAFT = "DRAFT";
    /** stocktake_order.status:盘点中(已开始录实盘,允许多次补录/修正) */
    public static final String STATUS_COUNTING = "COUNTING";
    /** stocktake_order.status:待调整(实盘已录齐,等待生成差异调整) */
    public static final String STATUS_PENDING_ADJUST = "PENDING_ADJUST";
    /** stocktake_order.status:已调整(差异已动账,凭证落 inventory_flow;不可改不可取消) */
    public static final String STATUS_ADJUSTED = "ADJUSTED";
    /** stocktake_order.status:已关闭(终态,差异已处理或明确放弃) */
    public static final String STATUS_CLOSED = "CLOSED";
    /** stocktake_order.status:已取消(旁路终态,未动账前可取消) */
    public static final String STATUS_CANCELED = "CANCELED";

    /** stocktake_order.scope_type:全仓(建单时按该仓 inventory 现有行快照) */
    public static final String SCOPE_ALL = "ALL";
    /** stocktake_order.scope_type:选定 SKU 集(建单时按入参 skuIds 快照,无库存行按 0 起) */
    public static final String SCOPE_SKU_SET = "SKU_SET";

    private StocktakeConsts() {
    }
}
