package com.own.erp.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableLogic;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 平台费率表(#19 预估费用模型:结算未回按费率估佣金,结算回后校差;无费率不估算禁猜)(platform_fee_rate)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("platform_fee_rate")
public class PlatformFeeRate {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 平台(PlatformType 枚举名) */
    private String platform;

    /** 费种:本期仅 COMMISSION 估佣金(对齐 settlement_detail.fee_type 词表;FBA 仓储类无费率不猜) */
    private String feeType;

    /** 站点,空=全站点(维度预留:估费 V1 只取全站点行,启用站点维随 #32 校差一并拍板) */
    private String marketplace;

    /** 类目路径,空=全类目(维度预留:估费 V1 只取全类目行) */
    private String categoryPath;

    /** 费率(如 0.150000=15%;必须大于0且小于1,Service 校验) */
    private BigDecimal rate;

    /** 生效起(含;按下单日回溯,同维取 eff_from 不晚于下单日的最新一条生效行) */
    private LocalDate effFrom;

    /** 生效止(含;空=长期有效) */
    private LocalDate effTo;

    /** 来源:MANUAL手工维护/CRAWLED抓取(预留扩展,本期仅 MANUAL) */
    private String source;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id),见 TODO#7 */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
