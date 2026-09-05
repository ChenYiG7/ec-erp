package com.own.erp.aftersale.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.aftersale.entity.AftersaleOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : aftersale_order 表 Mapper:状态机流转用条件更新(docs/07 §6.3 禁先查后改,
 *         WHERE 即状态机守卫,affected=0 = 单不存在或前置状态不符);updated_at 由表 ON UPDATE 兜底。
 *         同一 UPDATE 原子回填处理结果 result(人工处理入口 #12),SQL 内状态字面量与 AftersaleConsts 保持同步
 */
public interface AftersaleOrderMapper extends BaseMapper<AftersaleOrder> {

    /**
     * 平台售后同步幂等落库唯一写入口(#12):uk(shop_id, platform_refund_id) 冲突即更新(docs/07 铁律 5 禁先查后插);
     * 状态不整体覆盖,仅平台终态回传(REJECTED/CANCELLED)条件推进未决态单(平台撤单出口),
     * SQL 见 mapper/AftersaleOrderMapper.xml
     */
    int upsert(AftersaleOrder aftersaleOrder);

    /**
     * 单步状态流转并回填处理结果:agree(PENDING→APPROVED/RETURNING)、reject(PENDING→REJECTED)、
     * complete(REFUNDED→COMPLETED);
     * result 走 COALESCE:传 null 保留原值不抹前序动作留痕,传非 null 覆盖为最新
     */
    @Update("UPDATE aftersale_order SET status = #{toStatus}, result = COALESCE(#{result}, result) "
            + "WHERE id = #{id} AND status = #{fromStatus}")
    int casStatus(@Param("id") Long id, @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus, @Param("result") String result);

    /**
     * 收退件(#12 复合事务动作的占位步):RETURNING → RETURN_RECEIVED,同 UPDATE 回填退货入库仓 warehouse_id
     * (动账与明细落库同事务,见 AftersaleOrderService.receiveReturn);并发双收退件仅一个命中;
     * result 同 casStatus 走 COALESCE;状态字面量与 AftersaleConsts 保持同步
     */
    @Update("UPDATE aftersale_order SET status = 'RETURN_RECEIVED', warehouse_id = #{warehouseId}, "
            + "result = COALESCE(#{result}, result) WHERE id = #{id} AND status = 'RETURNING'")
    int receiveReturn(@Param("id") Long id, @Param("warehouseId") Long warehouseId, @Param("result") String result);

    /**
     * 退款:APPROVED(仅退款/补发类)或 RETURN_RECEIVED(退货退款/换货类,强制已收退件)→ REFUNDED;
     * 前置状态白名单即类型血缘校验,与 casStatus 的单前置态条件互斥;result 同 casStatus 走 COALESCE
     */
    @Update("UPDATE aftersale_order SET status = 'REFUNDED', result = COALESCE(#{result}, result) "
            + "WHERE id = #{id} AND status IN ('APPROVED', 'RETURN_RECEIVED')")
    int refundOrder(@Param("id") Long id, @Param("result") String result);
}
