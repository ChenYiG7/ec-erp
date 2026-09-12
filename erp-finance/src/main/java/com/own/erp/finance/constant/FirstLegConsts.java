package com.own.erp.finance.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程运费分摊域常量(#33,docs/plans/first-mile-freight.md):发货单状态机/分摊策略/仓型词表。
 *         字面量与 docs/sql/01_schema_init.sql first_leg_* 列 COMMENT、条件更新 SQL(@Update 字面量)保持同步。
 *         仓型词表源 = erp-warehouse warehouse.wh_type COMMENT,跨域不引对方模块常量,本处声明同形词面
 */
public final class FirstLegConsts {

    /** 状态:草稿(可改箱/删/装箱/取消) */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态:已装箱(可发货/取消,箱内容不可改) */
    public static final String STATUS_BOXED = "BOXED";
    /** 状态:已发货,运费/汇率已冻结(可分摊;不可取消/删除,逆向走作废重开后续拍板) */
    public static final String STATUS_SHIPPED = "SHIPPED";
    /** 状态:已分摊(first_leg_alloc 已落,不可重算覆盖) */
    public static final String STATUS_ALLOCATED = "ALLOCATED";
    /** 状态:已关闭(终态) */
    public static final String STATUS_CLOSED = "CLOSED";
    /** 状态:已取消(仅 DRAFT/BOXED 可达;旁路终态) */
    public static final String STATUS_CANCELED = "CANCELED";

    /** 分摊策略:按跨箱件数 */
    public static final String STRATEGY_QTY = "QTY";
    /** 分摊策略:按重量(qty×product_sku.weight_g,默认) */
    public static final String STRATEGY_WEIGHT = "WEIGHT";
    /** 分摊策略:按金额(qty×最近采购价,回退 product_sku.cost_price) */
    public static final String STRATEGY_AMOUNT = "AMOUNT";

    /** warehouse.wh_type:自仓(头程发货仓必须为本型) */
    public static final String WH_TYPE_SELF = "SELF";
    /** warehouse.wh_type:海外仓(头程目的仓型) */
    public static final String WH_TYPE_OVERSEAS = "OVERSEAS";
    /** warehouse.wh_type:FBA 仓(头程目的仓型) */
    public static final String WH_TYPE_FBA = "FBA";

    /** 头程单号前缀:FL+yyyyMMdd+4位seq */
    public static final String SHIPMENT_NO_PREFIX = "FL";

    private FirstLegConsts() {
    }
}
