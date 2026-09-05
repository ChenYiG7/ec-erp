package com.own.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.purchase.entity.PurchaseInbound;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : purchase_inbound 表 Mapper:状态流转用条件更新(WHERE 即守卫,
 *         confirm 并发双确认/重复确认靠 affected=0 拒绝,docs/07 §6.3 禁先查后改);
 *         updated_at 由表 ON UPDATE 兜底,SQL 内状态字面量与 PurchaseConsts 保持同步
 */
public interface PurchaseInboundMapper extends BaseMapper<PurchaseInbound> {

    /**
     * 单步状态流转:PENDING→RECEIVED(confirm 占位,失败回滚)/PENDING→CANCELLED(取消);
     * affected=0 = 单不存在或前置状态不符
     */
    @Update("UPDATE purchase_inbound SET status = #{toStatus} WHERE id = #{id} AND status = #{fromStatus}")
    int casStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);
}
