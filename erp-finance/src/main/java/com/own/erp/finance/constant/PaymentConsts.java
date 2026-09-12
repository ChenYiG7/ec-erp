package com.own.erp.finance.constant;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水域常量(#31 收付款/回款):方向/业务类型/往来方/状态词表与分摊/源单据类型。
 *         字面量与 docs/sql/01_schema_init.sql payment_record/payment_alloc 列 COMMENT、
 *         条件更新 SQL(@Update 字面量)保持同步
 */
public final class PaymentConsts {

    /** payment_record.direction:付款(钱流出) */
    public static final String DIRECTION_EXPENSE = "EXPENSE";
    /** payment_record.direction:回款(钱流入) */
    public static final String DIRECTION_INCOME = "INCOME";

    /** payment_record.biz_type:采购付款(经 payment_alloc 分摊到采购单) */
    public static final String BIZ_PURCHASE_PAYMENT = "PURCHASE_PAYMENT";
    /** payment_record.biz_type:结算回款(结算报告 PARSED 同事务自动派生) */
    public static final String BIZ_SETTLEMENT_RECEIPT = "SETTLEMENT_RECEIPT";
    /** payment_record.biz_type:手工调整(补录/其他打款) */
    public static final String BIZ_MANUAL_ADJUST = "MANUAL_ADJUST";

    /** payment_record.party_type:供应商 */
    public static final String PARTY_SUPPLIER = "SUPPLIER";
    /** payment_record.party_type:平台(店铺) */
    public static final String PARTY_PLATFORM = "PLATFORM";
    /** payment_record.party_type:其他往来方 */
    public static final String PARTY_OTHER = "OTHER";

    /** payment_record.status:正常(查流水默认只看本态) */
    public static final String STATUS_NORMAL = "NORMAL";
    /** payment_record.status:已作废(留痕禁物理删;对账口径含本态须显式注明) */
    public static final String STATUS_VOIDED = "VOIDED";

    /** payment_alloc.alloc_biz_type:采购单(purchase_order.id) */
    public static final String ALLOC_PURCHASE = "PURCHASE";

    /** payment_record.ref_type:结算报告(settlement_report.id,派生流水 uk_ref 幂等键) */
    public static final String REF_SETTLEMENT_REPORT = "SETTLEMENT_REPORT";

    /** 流水号前缀:PAY+yyyyMMdd+4位seq */
    public static final String PAYMENT_NO_PREFIX = "PAY";

    private PaymentConsts() {
    }
}
