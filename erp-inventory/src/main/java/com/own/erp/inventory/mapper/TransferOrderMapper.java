package com.own.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.inventory.entity.TransferOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : transfer_order 表 Mapper:状态流转用条件更新(WHERE 即守卫,docs/07 §6.3 禁先查后改,
 *         affected=0 = 单不存在或前置状态不符;CONFIRM 幂等/重复确认靠 affected=0 拦);
 *         updated_at 由表 ON UPDATE 兜底;SQL 内状态字面量与 TransferConsts 保持同步
 */
public interface TransferOrderMapper extends BaseMapper<TransferOrder> {

    /**
     * 单步状态流转:DRAFT→CONFIRMED(确认,复合事务入口占位)/DRAFT→CANCELED(取消);
     * affected=0 = 单不存在或前置状态不符
     */
    @Update("UPDATE transfer_order SET status = #{toStatus} WHERE id = #{id} AND status = #{fromStatus}")
    int casStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);

    /**
     * 行锁读(改单专用):FOR UPDATE 持行锁至提交——改单期间确认的 casStatus 阻塞在本行,
     * 二者天然串行化(先改单:确认等 commit 后按新明细双腿动账;先确认:改单行锁读到非 DRAFT 即拒)
     */
    @Select("SELECT * FROM transfer_order WHERE id = #{id} FOR UPDATE")
    TransferOrder selectByIdForUpdate(@Param("id") Long id);
}
