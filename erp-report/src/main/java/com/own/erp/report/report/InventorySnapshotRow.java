package com.own.erp.report.report;

import java.time.LocalDate;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 库存快照行(#20 报表域 V1):inventory_snapshot_daily 指定快照日全行(SU×仓粒度四量),
 *     名称翻译 LEFT JOIN product_sku/product/warehouse——join 不滤已删(#7 拍板,历史快照名字仍可读)
 */
public record InventorySnapshotRow(

        /** 快照日期 */
        LocalDate statDate,

        /** 内部SKU ID */
        Long skuId,

        /** 内部SKU编码 */
        String skuCode,

        /** SPU 商品名称 */
        String productName,

        /** 仓库ID */
        Long warehouseId,

        /** 仓库名称 */
        String whName,

        /** 在库快照 */
        int qtyOnHand,

        /** 占用快照 */
        int qtyLocked,

        /** 在途快照 */
        int qtyTransit,

        /** 可用快照=在库-占用 */
        int qtyAvailable
) {
}
