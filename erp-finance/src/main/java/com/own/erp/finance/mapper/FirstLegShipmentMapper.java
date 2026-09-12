package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.finance.entity.FirstLegShipment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : first_leg_shipment 表 Mapper:通用 CRUD(BaseMapper)+ 状态机条件更新
 *         (docs/07 §6.3 禁先查后改,WHERE 即守卫,affected=0 = 单据不存在或前置态不符)。
 *         状态机:DRAFT→BOXED→SHIPPED→ALLOCATED→CLOSED;CANCELED 旁路仅 DRAFT/BOXED 可达
 */
public interface FirstLegShipmentMapper extends BaseMapper<FirstLegShipment> {

    /** 装箱完成:DRAFT → BOXED;affected=0 = 单据不存在或非草稿态 */
    @Update("UPDATE first_leg_shipment SET status = 'BOXED' WHERE id = #{id} AND status = 'DRAFT'")
    int casBox(@Param("id") Long id);

    /** 确认发货:BOXED → SHIPPED(运费/汇率由 Service 同事务写前回填);affected=0 = 不存在或非已装箱态 */
    @Update("UPDATE first_leg_shipment SET status = 'SHIPPED' WHERE id = #{id} AND status = 'BOXED'")
    int casShip(@Param("id") Long id);

    /** 运费分摊:SHIPPED → ALLOCATED(first_leg_alloc 由 Service 同事务落库);affected=0 = 不存在或非已发货态(ALLOCATED 脱靶即拦重算) */
    @Update("UPDATE first_leg_shipment SET status = 'ALLOCATED' WHERE id = #{id} AND status = 'SHIPPED'")
    int casAllocate(@Param("id") Long id);

    /** 关闭:ALLOCATED → CLOSED;affected=0 = 不存在或非已分摊态 */
    @Update("UPDATE first_leg_shipment SET status = 'CLOSED' WHERE id = #{id} AND status = 'ALLOCATED'")
    int casClose(@Param("id") Long id);

    /** 取消:DRAFT/BOXED → CANCELED(SHIPPED 起不可取消,运费已录为财务事实);affected=0 = 不存在或前置态不符 */
    @Update("UPDATE first_leg_shipment SET status = 'CANCELED' WHERE id = #{id} AND status IN ('DRAFT','BOXED')")
    int casCancel(@Param("id") Long id);

    /**
     * 行锁读(改单专用):FOR UPDATE 持行锁至提交——改单与装箱/发货动作天然串行化
     * (同 TransferOrderMapper 先例,docs/07 §6.3)
     */
    @Select("SELECT * FROM first_leg_shipment WHERE id = #{id} FOR UPDATE")
    FirstLegShipment selectByIdForUpdate(@Param("id") Long id);
}
