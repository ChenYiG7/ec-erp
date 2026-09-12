package com.own.erp.fulfill.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 发货单域常量(docs/plans/fba-shipment.md,V1 内部数据面):状态机词表、库存流水关联业务类型。
 *         字面量与 docs/sql/01_schema_init.sql fba_* 列 COMMENT、条件更新 SQL(@Update 字面量)保持同步;
 *         仓型词表源 = erp-warehouse warehouse.wh_type COMMENT,跨域不引对方模块常量,本处声明同形词面
 */
public final class FbaConsts {

    /** 状态:草稿(可改计划/装箱/删/取消) */
    public static final String STATUS_DRAFT = "DRAFT";
    /** 状态:已装箱(可发货/取消,计划与箱内容冻结) */
    public static final String STATUS_BOXED = "BOXED";
    /** 状态:已发出(库存已 OUT_SHIP 动账;可收货登记,禁改禁删禁取消) */
    public static final String STATUS_SHIPPED = "SHIPPED";
    /** 状态:收货登记中(可重复登记覆盖 diff,可关闭) */
    public static final String STATUS_RECEIVING = "RECEIVING";
    /** 状态:已关闭(对账终态) */
    public static final String STATUS_CLOSED = "CLOSED";
    /** 状态:已取消(仅 DRAFT/BOXED 可达;旁路终态) */
    public static final String STATUS_CANCELED = "CANCELED";

    /** 收货对账差异类型:缺收(平台收货 < 发出) */
    public static final String DIFF_SHORT = "SHORT";
    /** 收货对账差异类型:多收(平台收货 > 发出) */
    public static final String DIFF_EXTRA = "EXTRA";
    /** 收货对账差异类型:一致 */
    public static final String DIFF_OK = "OK";

    /** inventory_flow.biz_type:FBA 发货出库(关联单据 = fba_shipment.id;词表随域常量,同 DeliveryConsts 先例) */
    public static final String BIZ_TYPE_FBA_SHIPMENT = "FBA_SHIPMENT";

    /** warehouse.wh_type:自仓(FBA 发货仓必须为本型,出库动账在此仓) */
    public static final String WH_TYPE_SELF = "SELF";

    /** FBA 单号前缀:FB+yyyyMMdd+4位seq */
    public static final String SHIPMENT_NO_PREFIX = "FB";

    private FbaConsts() {
    }
}
