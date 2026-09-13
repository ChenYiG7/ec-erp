package com.own.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.purchase.entity.PurchaseOrder;
import com.own.erp.purchase.response.PurchaseOverdueRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : purchase_order 表 Mapper:状态机流转用条件更新(docs/07 §6.3 禁先查后改,
 *         WHERE 即状态机守卫,affected=0 = 单不存在或前置状态不符);updated_at 由表 ON UPDATE 兜底。
 *         SQL 内状态字面量与 PurchaseConsts 保持同步
 */
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    /**
     * 单步状态流转:DRAFT→AUDITED(审核)、通用单前置态校验;affected=0 = 单不存在或前置状态不符
     */
    @Update("UPDATE purchase_order SET status = #{toStatus} WHERE id = #{id} AND status = #{fromStatus}")
    int casStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus);

    /**
     * 审核动作(#31 账期锚点):DRAFT→AUDITED 同步落 audit_time(审核时间=账期起算锚点);
     * WHERE 即状态机守卫,affected=0 = 单不存在或非草稿;audit_time 与状态同语句原子落库
     */
    @Update("UPDATE purchase_order SET status = 'AUDITED', audit_time = NOW() "
            + "WHERE id = #{id} AND status = 'DRAFT'")
    int auditOrder(@Param("id") Long id);

    /**
     * 超期未付清采购单(#31 账期到期提醒取数,SQL 形态见 mapper XML 注释口径):audit_time+settle_days
     * &lt; asOf 且未付清;按审核时间升序,LIMIT 500 防御
     */
    List<PurchaseOverdueRow> listOverduePayables(@Param("asOf") LocalDate asOf);

    /**
     * 入库核销推进:status ∈ {AUDITED, PARTIAL_RECEIVED} → toStatus(PARTIAL_RECEIVED/RECEIVED,由调用方按明细算出);
     * 与 closeOrder 的终态条件互斥,并发关闭时此处 affected=0 回滚核销事务
     */
    @Update("UPDATE purchase_order SET status = #{toStatus} WHERE id = #{poId} "
            + "AND status IN ('AUDITED', 'PARTIAL_RECEIVED')")
    int advanceOnReceive(@Param("poId") Long poId, @Param("toStatus") String toStatus);

    /**
     * 关闭采购单:AUDITED/PARTIAL_RECEIVED/RECEIVED → CLOSED(剩余量作废);DRAFT 单走删除,不可关闭
     */
    @Update("UPDATE purchase_order SET status = 'CLOSED' WHERE id = #{id} "
            + "AND status IN ('AUDITED', 'PARTIAL_RECEIVED', 'RECEIVED')")
    int closeOrder(@Param("id") Long id);
}
