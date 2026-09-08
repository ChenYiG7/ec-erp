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
 * @Description : 结算报告明细行(金额事件流水;报告级幂等——重拉按 report 先删后插同 #4 拉单明细纪律,
 *         行级不设唯一键:平台允许多行同键事件;金额报告原值带符号,禁取绝对值)(settlement_detail)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("settlement_detail")
public class SettlementDetail {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属结算报告ID(settlement_report.id) */
    private Long reportId;

    /** 店铺ID(冗余自报告同事务写入,免联查) */
    private Long shopId;

    /** 平台订单号(Amazon OrderId 原文;非订单事件如月租费/打款为NULL) */
    private String orderId;

    /** 平台订单行号(Amazon OrderItemId 原文,SKU级利润归集键,对应 shop_order_item.platform_order_item_id) */
    private String orderItemId;

    /** 平台SKU(报告原文,映射本地SKU随#5绑定关系联查,禁解析器猜) */
    private String sku;

    /**
     * 费用类型(解析器归一,只加不改:
     * SALE销售回款/REFUND退款/COMMISSION佣金/FBA_FEE履约费/STORAGE仓储费/ADVERTISING广告费/TRANSFER回款打款/OTHER其他)
     */
    private String feeType;

    /** 金额(报告原值带符号:正=收入/负=费用,禁取绝对值,勾稽=Σ本列) */
    private BigDecimal amount;

    /** 记账时间(报告 PostedDate) */
    private LocalDateTime postedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
