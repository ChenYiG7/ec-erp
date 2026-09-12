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
import com.baomidou.mybatisplus.annotation.TableLogic;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水(收付款/回款统一账;一单多付/一付多单经 payment_alloc 分摊)(payment_record)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("payment_record")
public class PaymentRecord {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流水号(PAY+yyyyMMdd+4位seq,服务端生成) */
    private String paymentNo;

    /** 资金方向:EXPENSE付款/INCOME回款 */
    private String direction;

    /** 业务类型:PURCHASE_PAYMENT采购付款/SETTLEMENT_RECEIPT结算回款/MANUAL_ADJUST手工调整 */
    private String bizType;

    /** 往来方类型:SUPPLIER供应商/PLATFORM平台/OTHER其他 */
    private String partyType;

    /** 往来方ID(supplier.id / shop.id;OTHER 可空) */
    private Long partyId;

    /** 原币金额(恒正,收付方向看 direction) */
    private BigDecimal amount;

    /** 币种(ISO 4217;采购付款强制CNY与本位币采购总额勾稽) */
    private String currency;

    /** 折算汇率快照(1 currency=rate CNY;落库时 resolveRate 按 paid_at 回溯冻结,CNY=1,无报价NULL) */
    private BigDecimal exchangeRate;

    /** 折算本位币金额(缺汇率为NULL,查询面缺口计数不静默) */
    private BigDecimal amountCny;

    /** 收付款时间 */
    private LocalDateTime paidAt;

    /** 结算方式(银行转账/支付宝/平台打款等,走 sys_dict) */
    private String method;

    /** 源单据类型:SETTLEMENT_REPORT结算报告(派生流水幂等键) */
    private String refType;

    /** 源单据ID(settlement_report.id) */
    private Long refId;

    /** 状态:NORMAL正常/VOIDED已作废(作废留痕禁物理删;分摊随作废经 join NORMAL 失效) */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人(sys_user.id;系统派生流水为NULL) */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除:0=正常,非0=已删(值=被删行id),见 TODO#7 */
    @TableLogic(value = "0", delval = "id")
    private Long deleted;
}
