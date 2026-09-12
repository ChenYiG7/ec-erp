package com.own.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水分摊(一付多单;采购单已付=Σ本表关联NORMAL流水,查询时聚合不冗余存储)(payment_alloc)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("payment_alloc")
public class PaymentAlloc {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资金流水ID(payment_record.id) */
    private Long paymentId;

    /** 分摊业务类型:PURCHASE采购单 */
    private String allocBizType;

    /** 分摊业务单据ID(purchase_order.id) */
    private Long allocBizId;

    /** 分摊金额(同流水原币;Σ分摊≤流水金额允许部分挂账;按单已付+本次≤采购总额超额拦截) */
    private BigDecimal amount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
