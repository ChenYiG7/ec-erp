package com.own.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 库存日快照(inventory_snapshot_daily,#6 库存快照数据面,docs/03 §7.2):
 *     快照日×SKU×仓库 存量四量(在库/占用/在途/可用);erp-api InventorySnapshotJob 每日低峰 upsert
 *     (uk_sku_wh_date 幂等,同日重跑覆盖当日值),读侧只读契约 InventorySnapshotQueryApi。
 *     ⚠️ **只增不可回溯**:快照取的是"当下存量",历史日期无法重算(要回溯需由 inventory_flow 逐日反推,
 *     V2 再评估)——故不提供窗口重算,与销量面 order_sales_daily 的"可重算窗口"是两种语义
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射,@Builder 供纯构造装配位;
 * 双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("inventory_snapshot_daily")
public class InventorySnapshotDaily {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 快照日期 */
    private LocalDate statDate;

    /** 内部SKU ID(product_sku.id) */
    private Long skuId;

    /** 仓库ID(warehouse.id) */
    private Long warehouseId;

    /** 在库快照 */
    private Integer qtyOnHand;

    /** 占用快照(发货单占用未发货) */
    private Integer qtyLocked;

    /** 在途快照(采购审核占用未入库) */
    private Integer qtyTransit;

    /** 可用快照 = 在库 - 占用 */
    private Integer qtyAvailable;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
