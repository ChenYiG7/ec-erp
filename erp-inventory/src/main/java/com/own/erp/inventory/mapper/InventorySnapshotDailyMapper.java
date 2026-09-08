package com.own.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.inventory.entity.InventorySnapshotDaily;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 库存日快照 Mapper(inventory_snapshot_daily,#6 库存快照数据面):通用 CRUD 走 BaseMapper,
 *     每日快照走 XML upsert(uk_sku_wh_date 冲突即覆盖当日值,同日重跑幂等),读侧序列/分页走 XML。
 *     与销量面差异:不提供窗口重算——存量快照取"当下值",历史不可回溯(见实体 javadoc)
 */
public interface InventorySnapshotDailyMapper extends BaseMapper<InventorySnapshotDaily> {

    /**
     * 按日期存量快照:把当日 inventory 全量四量按 (sku_id, warehouse_id) 落快照,
     * uk_sku_wh_date 冲突即覆盖(同日重跑/补跑幂等);返回受影响行数
     */
    int upsertSnapshot(@Param("statDate") LocalDate statDate);

    /**
     * 单 SKU 单仓时间序(按日期升序,趋势/周转率用):limit 钳制防长区间拉爆结果集;
     * warehouseId 为 null 时按 SKU 跨仓聚合(SUM 四量)
     */
    List<InventorySnapshotDaily> listSeries(@Param("skuId") Long skuId,
                                            @Param("warehouseId") Long warehouseId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            @Param("limit") int limit);
}
