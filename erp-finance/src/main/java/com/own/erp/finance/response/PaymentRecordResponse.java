package com.own.erp.finance.response;

import com.own.erp.finance.entity.PaymentRecord;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 资金流水对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进组件即编译期封死
 */
@Builder
public record PaymentRecordResponse(

        /** 主键 */
        Long id,

        /** 流水号(PAY+yyyyMMdd+4位seq,服务端生成) */
        String paymentNo,

        /** 资金方向:EXPENSE付款/INCOME回款 */
        String direction,

        /** 业务类型:PURCHASE_PAYMENT采购付款/SETTLEMENT_RECEIPT结算回款/MANUAL_ADJUST手工调整 */
        String bizType,

        /** 往来方类型:SUPPLIER供应商/PLATFORM平台/OTHER其他 */
        String partyType,

        /** 往来方ID(supplier.id / shop.id;OTHER 可空) */
        Long partyId,

        /** 原币金额(恒正,收付方向看 direction) */
        BigDecimal amount,

        /** 币种(ISO 4217;采购付款强制CNY与本位币采购总额勾稽) */
        String currency,

        /** 折算汇率快照(1 currency=rate CNY;落库时 resolveRate 按 paid_at 回溯冻结,CNY=1,无报价NULL) */
        BigDecimal exchangeRate,

        /** 折算本位币金额(缺汇率为NULL,查询面缺口计数不静默) */
        BigDecimal amountCny,

        /** 收付款时间 */
        LocalDateTime paidAt,

        /** 结算方式(银行转账/支付宝/平台打款等,走 sys_dict) */
        String method,

        /** 源单据类型:SETTLEMENT_REPORT结算报告(派生流水幂等键) */
        String refType,

        /** 源单据ID(settlement_report.id) */
        Long refId,

        /** 状态:NORMAL正常/VOIDED已作废(作废留痕禁物理删;分摊随作废经 join NORMAL 失效) */
        String status,

        /** 备注 */
        String remark,

        /** 创建人(sys_user.id;系统派生流水为NULL) */
        Long createdBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 往来方名称(查询面 join supplier/shop 装配,SUPPLIER=供应商名/PLATFORM=店铺名/OTHER=null) */
        String partyName

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);单实体读取无 partyName,列表走 XML join 装配 */
    public static PaymentRecordResponse from(PaymentRecord entity) {
        return PaymentRecordResponse.builder()
                .id(entity.getId())
                .paymentNo(entity.getPaymentNo())
                .direction(entity.getDirection())
                .bizType(entity.getBizType())
                .partyType(entity.getPartyType())
                .partyId(entity.getPartyId())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .exchangeRate(entity.getExchangeRate())
                .amountCny(entity.getAmountCny())
                .paidAt(entity.getPaidAt())
                .method(entity.getMethod())
                .refType(entity.getRefType())
                .refId(entity.getRefId())
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** wither 副本:列表 XML join 装配往来方名/详情挂分摊,禁为补字段回退可变模型(docs/07 §1 分级③) */
    public PaymentRecordResponse withPartyName(String partyName) {
        return new PaymentRecordResponse(id, paymentNo, direction, bizType, partyType, partyId, amount, currency,
                exchangeRate, amountCny, paidAt, method, refType, refId, status, remark, createdBy,
                createdAt, updatedAt, partyName);
    }
}
