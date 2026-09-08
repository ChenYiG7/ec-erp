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
 * @Date : 2026/9/8
 * @Description : 汇率快照(多币种折算依据;汇率是快照不是现值——折算一律按业务日回溯取最近报价,
 *         禁取表内最新一条;记账本位币 V1 固定 CNY)(exchange_rate)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("exchange_rate")
public class ExchangeRate {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 币种(ISO 4217) */
    private String currency;

    /** 汇率快照(1 currency = rate CNY;本位币V1固定CNY拍板,扩多本位币随四期评估) */
    private BigDecimal rate;

    /** 报价时间(利润折算口径=取 quoted_at<=业务日(下单日/记账日)的最近一条) */
    private LocalDateTime quotedAt;

    /** MANUAL手工录入/API行情接口(随行情源接入评估,先手工维护) */
    private String source;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
