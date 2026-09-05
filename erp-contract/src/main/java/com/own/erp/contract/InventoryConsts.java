package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 库存变更契约常量(inventory_flow.flow_type 词表随契约走,动库存的业务域共用):
 *         采购入库 #10 用 IN_PURCHASE;发货出库 #11 用 OUT_SHIP;售后退货入库 #12 用 IN_RETURN。
 *         字面量与 docs/03 §4 库存流水 DDL 注释、01_schema_init.sql 保持同步
 */
public final class InventoryConsts {

    /** inventory_flow.flow_type:采购入库 */
    public static final String FLOW_TYPE_IN_PURCHASE = "IN_PURCHASE";
    /** inventory_flow.flow_type:销售发货出库 */
    public static final String FLOW_TYPE_OUT_SHIP = "OUT_SHIP";
    /** inventory_flow.flow_type:售后退货入库 */
    public static final String FLOW_TYPE_IN_RETURN = "IN_RETURN";
    /** inventory_flow.flow_type:人工调整(可正可负) */
    public static final String FLOW_TYPE_ADJUST = "ADJUST";
    /** inventory_flow.flow_type:调拨出库(与 TRANSFER_IN 成对,由上层组合两次 change) */
    public static final String FLOW_TYPE_TRANSFER_OUT = "TRANSFER_OUT";
    /** inventory_flow.flow_type:调拨入库 */
    public static final String FLOW_TYPE_TRANSFER_IN = "TRANSFER_IN";

    private InventoryConsts() {
    }
}
