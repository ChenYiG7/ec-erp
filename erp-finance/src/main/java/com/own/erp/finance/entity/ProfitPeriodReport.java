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
 * @Description : 周期利润报告(#19 三口径第二层:结算报告期粒度,订单口径vs结算口径校差;系统写入对外只读)(profit_period_report)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("profit_period_report")
public class ProfitPeriodReport {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID(shop.id) */
    private Long shopId;

    /** 关联结算报告ID(settlement_report.id) */
    private Long settlementId;

    /** 周期起(=结算报告 period_start) */
    private LocalDateTime periodStart;

    /** 周期止(=结算报告 period_end) */
    private LocalDateTime periodEnd;

    /** 结算原生币种(ISO 4217;按原币聚合后统一折算 CNY) */
    private String currency;

    /** 折算CNY汇率快照(周期止回溯 resolveRate 冻结,1 currency=rate CNY,CNY=1;无报价NULL禁猜) */
    private BigDecimal rateUsed;

    /** 缺汇率标记:0否 1是(1时CNY列留NULL,缺口计数不静默归零) */
    private Integer rateMissing;

    /** 订单口径收入(CNY;周期窗内已支付态订单行售价合计) */
    private BigDecimal orderIncome;

    /** 结算口径回款(CNY;TRANSFER 行折算,报告原符号) */
    private BigDecimal settleIncome;

    /** 结算侧佣金(CNY;COMMISSION 行带符号合计,佣金为负) */
    private BigDecimal settleCommission;

    /** FBA系费用(CNY;FBA_FEE/STORAGE 行带符号合计,费用为负) */
    private BigDecimal fbaFee;

    /** 其他费用(CNY;REFUND/ADVERTISING/OTHER等费种带符号合计,SALE单列进收入差/TRANSFER不进差值,#32 拍板④A) */
    private BigDecimal otherFee;

    /** 订单口径佣金(CNY;周期窗内订单行实际佣金+费率预估佣金合计) */
    private BigDecimal orderCommission;

    /** 订单口径利润(CNY;周期窗内订单行利润合计,预估参与时带ESTIMATED语义) */
    private BigDecimal orderProfit;

    /** 收入校差(CNY;结算SALE行合计-订单收入,同号相减正=结算侧多,超0.01容差置diff_flag,#32 拍板④A) */
    private BigDecimal diffIncome;

    /** 佣金校差(CNY;结算佣金-订单归集佣金,同号相减正=结算侧多,超0.01容差置diff_flag) */
    private BigDecimal diffCommission;

    /** 校差超容差标记:0勾稽平 1有差异(容差0.01本位币,同退款勾稽防尾差) */
    private Integer diffFlag;

    /** 校差说明(差异项/跨期口径与缺口计数,人工复核入口) */
    private String diffRemark;

    /** 状态:OK勾稽平/DIFF有差异/RATE_MISSING缺汇率(三态RATE_MISSING优先,#32 拍板③) */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
