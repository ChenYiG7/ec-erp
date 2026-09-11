package com.own.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.inventory.entity.StocktakeOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : stocktake_order 表 Mapper:状态流转用条件更新(WHERE 即守卫,docs/07 §6.3 禁先查后改,
 *         affected=0 = 单不存在或前置状态不符);updated_at 由表 ON UPDATE 兜底。
 *         SQL 内状态字面量与 StocktakeConsts 保持同步
 */
public interface StocktakeOrderMapper extends BaseMapper<StocktakeOrder> {

    /**
     * 单步状态流转:DRAFT→COUNTING(开始盘点)/COUNTING→PENDING_ADJUST(实盘录齐)/PENDING_ADJUST→ADJUSTED
     * (生成调整,复合事务入口占位)/ADJUSTED→CLOSED(关闭)/→CANCELED(取消,前置态由调用方按未动账三态给值);
     * affected=0 = 单不存在或前置状态不符
     */
    @Update("UPDATE stocktake_order SET status = #{toStatus} WHERE id = #{id} AND status = #{fromStatus}")
    int casStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);

    /**
     * 行锁读(改单专用):FOR UPDATE 持行锁至提交——改单重做快照期间"开始盘点/生成调整"的 casStatus 阻塞在本行,
     * 二者天然串行化(先改单:后续动作等 commit 后按新快照走;先动账:改单行锁读到非 DRAFT 即拒)
     */
    @Select("SELECT * FROM stocktake_order WHERE id = #{id} FOR UPDATE")
    StocktakeOrder selectByIdForUpdate(@Param("id") Long id);
}
