package com.own.erp.fulfill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.fulfill.entity.DeliveryOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : delivery_order 表 Mapper:通用 CRUD 走 BaseMapper;状态流转用条件更新(WHERE 即守卫,
 *         ship 并发双确认/重复确认靠 affected=0 拒绝,docs/07 §6.3 禁先查后改);
 *         updated_at 由表 ON UPDATE 兜底,SQL 内状态字面量与 DeliveryConsts 保持同步
 */
public interface DeliveryOrderMapper extends BaseMapper<DeliveryOrder> {

    /**
     * 单步状态流转:PENDING→SHIPPED(ship 占位,失败回滚)/PENDING→CANCELLED(取消)/SHIPPED→DELIVERED(签收);
     * affected=0 = 单不存在或前置状态不符
     */
    @Update("UPDATE delivery_order SET status = #{toStatus} WHERE id = #{id} AND status = #{fromStatus}")
    int casStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
}
