package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 库存变更契约常量(inventory_flow.flow_type 词表随契约走,动库存的业务域共用):
 *         采购入库 #10 用 IN_PURCHASE(入库核销在途);发货出库 #11 用 OUT_SHIP(占用转出库);
 *         售后退货入库 #12 用 IN_RETURN;采购审核占在途/关闭释放 #10 用 IN_TRANSIT;
 *         发货单建单占用/取消释放 #11 用 LOCK_SHIP。
 *         字面量与 docs/03 §4 库存流水 DDL 注释、01_schema_init.sql 保持同步。
 *         biz_type(关联业务类型)同理随契约走:单据域自持常量者(采购/发货)在各自域常量类,
 *         仓内作业域(盘点/调拨)由本类收口(InventoryService.transfer 亦引用,防模块内硬编码漂移)
 */
public final class InventoryConsts {

    /** inventory_flow.flow_type:采购入库(核销在途:在途-q、在库+q、可用+q;守卫=在途充足) */
    public static final String FLOW_TYPE_IN_PURCHASE = "IN_PURCHASE";
    /** inventory_flow.flow_type:销售发货出库(占用转出库:在库-q、占用-q,可用不变;守卫=在库/占用充足) */
    public static final String FLOW_TYPE_OUT_SHIP = "OUT_SHIP";
    /** inventory_flow.flow_type:售后退货入库(在库+q、可用+q) */
    public static final String FLOW_TYPE_IN_RETURN = "IN_RETURN";
    /** inventory_flow.flow_type:人工调整(可正可负,在库+q、可用+q) */
    public static final String FLOW_TYPE_ADJUST = "ADJUST";
    /** inventory_flow.flow_type:调拨出库(负数,与 TRANSFER_IN 成对,上层组合走 InventoryService.transfer) */
    public static final String FLOW_TYPE_TRANSFER_OUT = "TRANSFER_OUT";
    /** inventory_flow.flow_type:调拨入库(正数,同上) */
    public static final String FLOW_TYPE_TRANSFER_IN = "TRANSFER_IN";
    /** inventory_flow.flow_type:采购在途(审核占 +q/关闭释放 -q,仅动 qty_transit;#10,#7 2026-09-06) */
    public static final String FLOW_TYPE_IN_TRANSIT = "IN_TRANSIT";
    /** inventory_flow.flow_type:发货单占用(建单 +q:占用+q/可用-q;取消·删除·改单释放 -q;#11,#7 2026-09-06) */
    public static final String FLOW_TYPE_LOCK_SHIP = "LOCK_SHIP";

    /** inventory_flow.biz_type:盘点单(差异 ADJUST 动账,关联单据 = stocktake_order.id;2026-09-11 仓内作业) */
    public static final String BIZ_TYPE_STOCKTAKE = "STOCKTAKE";
    /** inventory_flow.biz_type:调拨单(两腿 TRANSFER_OUT/IN,关联单据 = transfer_order.id;2026-09-11 收口自 INVENTORY_TRANSFER 字面量) */
    public static final String BIZ_TYPE_TRANSFER_ORDER = "TRANSFER_ORDER";

    private InventoryConsts() {
    }
}
