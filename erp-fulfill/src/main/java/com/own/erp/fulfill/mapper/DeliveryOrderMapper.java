package com.own.erp.fulfill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.fulfill.entity.DeliveryOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 行锁读(改单专用,#7 占用/释放入场后禁 check-then-act 串状态):
     * FOR UPDATE 持行锁至提交——改单释放/重占期间 ship 的 casStatus 会阻塞在本行,
     * 二者天然串行化(先改单:ship 等 commit 后照常核销新占用;先 ship:改单行锁读到 SHIPPED 即拒),
     * 防"改单释放了已发货单据的占用"库存错账
     */
    @Select("SELECT * FROM delivery_order WHERE id = #{id} FOR UPDATE")
    DeliveryOrder selectByIdForUpdate(@Param("id") Long id);

    /**
     * 回传成功落状态(#11 激活期余量):条件更新守卫(仅 PENDING/FAILED 可翻 SUCCESS,docs/07 §6.3),
     * 幂等——重复成功事件/补偿扫并发同单 affected=0 静默;失败原因成功即清
     */
    @Update("UPDATE delivery_order SET sync_status = 'SUCCESS', sync_fail_reason = NULL, sync_time = #{now} "
            + "WHERE id = #{id} AND sync_status IN ('PENDING', 'FAILED')")
    int markSyncSuccess(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 回传失败落状态(#11 激活期余量):FAILED + 失败原因 + 重试计数+1 + 尝试时间(补偿扫退避基准);
     * 计数在 SQL 内自增防并发丢更新,不收客户端值
     */
    @Update("UPDATE delivery_order SET sync_status = 'FAILED', sync_fail_reason = #{reason}, "
            + "sync_retry_count = sync_retry_count + 1, sync_time = #{now} WHERE id = #{id}")
    int markSyncFailed(@Param("id") Long id, @Param("reason") String reason, @Param("now") LocalDateTime now);

    /**
     * 补偿扫候选单(#11 激活期余量):已发货/已签收 且 待回传或失败,退避未到期(距上次尝试不足
     * retry_count×backoff 分钟)不选、重试达上限不选(保持 FAILED 待人工)、无运单号不选
     * (要素未齐回传无意义,且防无进度单长期占用批次);sync_time 为空(从未尝试,如事件丢失)= 立即到期。
     * 存量 sync_status NULL(迁移前已发货)不回溯。仅取 ID,编排细节在 ShipmentSyncService
     */
    @Select("SELECT id FROM delivery_order WHERE status IN ('SHIPPED', 'DELIVERED') "
            + "AND sync_status IN ('PENDING', 'FAILED') AND sync_retry_count < #{maxRetries} "
            + "AND tracking_no IS NOT NULL AND tracking_no != '' "
            + "AND (sync_time IS NULL OR TIMESTAMPDIFF(MINUTE, sync_time, #{now}) >= sync_retry_count * #{backoffMinutes}) "
            + "ORDER BY id LIMIT #{limit}")
    List<Long> selectSyncRetryCandidateIds(@Param("maxRetries") int maxRetries,
                                           @Param("backoffMinutes") int backoffMinutes,
                                           @Param("now") LocalDateTime now,
                                           @Param("limit") int limit);
}
