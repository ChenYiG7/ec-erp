package com.own.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.own.erp.finance.entity.PaymentRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : payment_record 表 Mapper:通用 CRUD(BaseMapper)+ 作废条件更新
 *         (docs/07 §6.3 禁先查后改,WHERE 即守卫,affected=0 = 流水不存在或非 NORMAL);
 *         禁物理删除——登记错误走作废留痕(updated_at 由表 ON UPDATE 兜底)
 */
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    /**
     * 作废:NORMAL → VOIDED 条件更新;affected=0 = 流水不存在或已作废/已逻辑删除。
     * 分摊行不物理删(join NORMAL 流水自然失效,审计可回溯)
     */
    @Update("UPDATE payment_record SET status = 'VOIDED' WHERE id = #{id} AND status = 'NORMAL' AND deleted = 0")
    int casVoid(@Param("id") Long id);
}
