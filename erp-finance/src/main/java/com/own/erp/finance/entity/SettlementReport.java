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
 * @Description : 平台结算报告(结算周期正本,周期利润数据源;幂等:uk_shop_settlement 重拉 upsert;
 *         勾稽纪律:Σ明细金额=报告头 TotalAmount 才算入库,否则 FAILED 可重拉,禁静默截断)(settlement_report)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("settlement_report")
public class SettlementReport {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID(shop.id) */
    private Long shopId;

    /** 平台结算批次号(Amazon SettlementId,幂等键,重拉 upsert) */
    private String settlementId;

    /** 结算周期起(报告 StartDate) */
    private LocalDateTime periodStart;

    /** 结算周期止(报告 EndDate) */
    private LocalDateTime periodEnd;

    /** 结算币种(ISO 4217,Amazon 按站点单一币种,站点映射收口 AmazonMarketplace) */
    private String currency;

    /** 报告汇总总额(报告头 TotalAmount 原值含正负,勾稽基准=Σ明细金额) */
    private BigDecimal totalAmount;

    /** 费用小计(Σ负项明细绝对值) */
    private BigDecimal feeAmount;

    /** 回款净额(Σ fee_type=TRANSFER 明细;预留金随真凭证实测校准) */
    private BigDecimal transferAmount;

    /** 原始报告文件地址(S3 预签名URL会过期,仅审计留痕) */
    private String rawFileUrl;

    /** PARSED解析入库(Σ明细=汇总校验平)/FAILED解析或勾稽不平(可重拉覆盖) */
    private String status;

    /** 报告拉取时间 */
    private LocalDateTime pulledAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
