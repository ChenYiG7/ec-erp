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
 * @Description : 头程运费分摊结果(#33 路线B 期间费用行;利润第三层费用聚合源,append-only)(first_leg_alloc)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("first_leg_alloc")
public class FirstLegAlloc {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 头程单ID(first_leg_shipment.id) */
    private Long shipmentId;

    /** SKU ID(product_sku.id) */
    private Long skuId;

    /** 分摊头程运费(CNY;Σ本列=freight_cny,尾差并入最大基数行,容差0.01) */
    private BigDecimal allocAmount;

    /** 分摊基数快照(实际策略口径:QTY=件数/WEIGHT=总重g/AMOUNT=采购金额CNY) */
    private BigDecimal allocBase;

    /** 实际生效策略:QTY/WEIGHT/AMOUNT(全0分母降级后可能与主单 allocate_strategy 不同) */
    private String strategy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
