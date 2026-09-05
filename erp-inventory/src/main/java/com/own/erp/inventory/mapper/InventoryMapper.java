package com.own.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : inventory 表 Mapper:库存变更原子更新(updateAvailableDelta,change() 存量行分支专用)
 */
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 库存可用量原子增减(docs/07 §1 ① 正确性锁落 DB,禁 check-then-act):
     * 余额条件 `qty_available + Δ >= 0` 进 WHERE,行锁天然串行化同行并发变更;
     * affected=0 = 行不存在或余额不足,由调用方回查区分;
     * updated_at 由表 ON UPDATE CURRENT_TIMESTAMP 维护;qty_locked/qty_transit 不在此路改动(TODO #7)
     */
    @Update("UPDATE inventory SET qty_on_hand = qty_on_hand + #{quantity}, qty_available = qty_available + #{quantity} "
            + "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND qty_available + #{quantity} >= 0")
    int updateAvailableDelta(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId,
                             @Param("quantity") Integer quantity);
}
