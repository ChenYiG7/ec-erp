package com.own.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : inventory 表 Mapper:库存变更原子更新(change() 存量行分支专用,#7 按 flow_type 分发)。
 *         每类流水一条原子 UPDATE:算术与守卫条件全下 SQL,行锁天然串行化同行并发变更;
 *         affected=0 = 行不存在或守卫不足,由调用方回查区分;
 *         updated_at 由表 ON UPDATE CURRENT_TIMESTAMP 维护;
 *         不变量 qty_available = qty_on_hand - qty_locked 对全部语句成立(LOCK_SHIP/OUT_SHIP 双列同向抵消)
 */
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 可用量原子增减(通用形态:IN_RETURN/ADJUST/TRANSFER_OUT/TRANSFER_IN,可正可负):
     * 在库+Δ、可用+Δ,余额条件 `qty_available + Δ >= 0` 进 WHERE
     */
    @Update("UPDATE inventory SET qty_on_hand = qty_on_hand + #{quantity}, qty_available = qty_available + #{quantity} "
            + "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND qty_available + #{quantity} >= 0")
    int updateAvailableDelta(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId,
                             @Param("quantity") Integer quantity);

    /**
     * 在途占/释(IN_TRANSIT,采购审核 +Δ 占用/关闭 -Δ 释放,#7 2026-09-06):
     * 仅动 qty_transit,在库/占用/可用均不变,无守卫(负数释放量由调用方按未到货量给值,正向多占无害,
     * 入库核销侧 receiveInbound 有在途充足守卫兜底)
     */
    @Update("UPDATE inventory SET qty_transit = qty_transit + #{quantity} "
            + "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId}")
    int updateTransitDelta(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId,
                           @Param("quantity") Integer quantity);

    /**
     * 入库核销在途(IN_PURCHASE,#7 2026-09-06):在途-Δ、在库+Δ、可用+Δ,
     * 守卫 `qty_transit >= Δ` 进 WHERE——未审核占在途的存量数据/旁路入库在此拦截
     */
    @Update("UPDATE inventory SET qty_transit = qty_transit - #{quantity}, "
            + "qty_on_hand = qty_on_hand + #{quantity}, qty_available = qty_available + #{quantity} "
            + "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND qty_transit >= #{quantity}")
    int receiveInbound(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId,
                       @Param("quantity") Integer quantity);

    /**
     * 发货占用/释放(LOCK_SHIP,建单 +Δ 占用/取消·删除·改单 -Δ 释放,#11+2026-09-06 #7):
     * 占用+Δ、可用-Δ,守卫 `qty_available - Δ >= 0` 进 WHERE(负数释放时条件恒成立,无副作用)
     */
    @Update("UPDATE inventory SET qty_locked = qty_locked + #{quantity}, qty_available = qty_available - #{quantity} "
            + "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND qty_available - #{quantity} >= 0")
    int lockForShip(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId,
                    @Param("quantity") Integer quantity);

    /**
     * 出库核销占用(OUT_SHIP,数量为负,#7 2026-09-06):在库+Δ、占用+Δ(Δ<0 即双降),可用不变
     * (建单占用时已扣);守卫在库/占用均充足进 WHERE,占用不足=未占用先发货,在此拦截
     */
    @Update("UPDATE inventory SET qty_on_hand = qty_on_hand + #{quantity}, qty_locked = qty_locked + #{quantity} "
            + "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} "
            + "AND qty_on_hand + #{quantity} >= 0 AND qty_locked + #{quantity} >= 0")
    int shipLockedOut(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId,
                      @Param("quantity") Integer quantity);
}
