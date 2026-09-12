package com.own.erp.fulfill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.fulfill.entity.FbaShipment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : fba_shipment 表 Mapper:通用 CRUD(BaseMapper)+ 状态机条件更新
 *         (docs/07 §6.3 禁先查后改,WHERE 即守卫,affected=0 = 单据不存在或前置态不符)。
 *         状态机:DRAFT→BOXED→SHIPPED→RECEIVING→CLOSED;CANCELED 旁路仅 DRAFT/BOXED 可达
 */
public interface FbaShipmentMapper extends BaseMapper<FbaShipment> {

    /** 装箱完成:DRAFT → BOXED;affected=0 = 单据不存在或非草稿态 */
    @Update("UPDATE fba_shipment SET status = 'BOXED' WHERE id = #{id} AND status = 'DRAFT'")
    int casBox(@Param("id") Long id);

    /** 确认发出:BOXED → SHIPPED(shipped_at 与逐 SKU OUT_SHIP 动账由 Service 同事务写;affected=0 = 不存在或非已装箱态) */
    @Update("UPDATE fba_shipment SET status = 'SHIPPED' WHERE id = #{id} AND status = 'BOXED'")
    int casShip(@Param("id") Long id);

    /** 收货登记:SHIPPED → RECEIVING(首次登记;RECEIVING 态重复登记不经此 cas,Service 放行覆盖);affected=0 = 不存在或非已发出态 */
    @Update("UPDATE fba_shipment SET status = 'RECEIVING' WHERE id = #{id} AND status = 'SHIPPED'")
    int casReceive(@Param("id") Long id);

    /** 关闭:RECEIVING → CLOSED(diff 已落,对账事实冻结);affected=0 = 不存在或非收货登记中态 */
    @Update("UPDATE fba_shipment SET status = 'CLOSED' WHERE id = #{id} AND status = 'RECEIVING'")
    int casClose(@Param("id") Long id);

    /** 取消:DRAFT/BOXED → CANCELED(SHIPPED 起库存已动账禁取消);affected=0 = 不存在或前置态不符 */
    @Update("UPDATE fba_shipment SET status = 'CANCELED' WHERE id = #{id} AND status IN ('DRAFT','BOXED')")
    int casCancel(@Param("id") Long id);

    /**
     * 行锁读(改单专用):FOR UPDATE 持行锁至提交——改单与装箱/发货动作天然串行化
     * (同 FirstLegShipmentMapper 先例,docs/07 §6.3)
     */
    @Select("SELECT * FROM fba_shipment WHERE id = #{id} FOR UPDATE")
    FbaShipment selectByIdForUpdate(@Param("id") Long id);
}
