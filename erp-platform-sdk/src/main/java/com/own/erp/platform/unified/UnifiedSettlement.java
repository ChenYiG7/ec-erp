package com.own.erp.platform.unified;

import com.own.erp.platform.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 统一结算报告模型(#19 财务/结算域)。
 *      结算报告 = 平台按打款周期自动生成的离散费用正本(不可主动创建,只能搜索已生成报告),
 *      一份报告 = 结算头(周期/币种/总额)+ 金额事件流水;SKU 级利润按 orderItemId 归集。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedSettlement {

    /** 平台结算批次号(Amazon SettlementId,落库幂等键) */
    private String settlementId;

    private Long shopId;

    private PlatformType platform;

    /** 结算周期起(报告 settlement-start-date) */
    private Instant periodStart;

    /** 结算周期止(报告 settlement-end-date) */
    private Instant periodEnd;

    /** 预计打款日(报告 deposit-date;实际到账随对账拍板,V1 仅留档) */
    private Instant depositDate;

    /** 结算币种(ISO 4217,报告单币种) */
    private String currency;

    /** 报告头 total-amount 原值(含正负;勾稽基准=Σ明细金额,不平整单 FAILED 留痕可重拉) */
    private BigDecimal totalAmount;

    /** 金额事件流水(行级无唯一键,平台允许多行同键事件) */
    private List<Line> lines;

    /**
     * 费用类型(解析器归一,值集只加不改,与 settlement_detail.fee_type 一致):
     * 平台原始维度 = transaction-type × amount-type × amount-description,映射规则收口 AmazonSettlementTranslator
     */
    public enum FeeType {
        SALE,         // 销售回款(含运费/税费/促销等订单侧正负收入流)
        REFUND,       // 退款
        COMMISSION,   // 平台佣金(含 RefundCommission)
        FBA_FEE,      // FBA 履约费(FBA*Fee 族)
        STORAGE,      // 仓储费(Storage Fee)
        ADVERTISING,  // 广告费(ABA-* 族)
        TRANSFER,     // 回款打款(预留金变动等打款方向事件)
        OTHER         // 未归一兜底(新费用类型先落 OTHER 不丢数据,随真凭证观测扩值)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        /** 平台订单号(非订单事件如月租费/打款为 null) */
        private String orderId;
        /** 平台订单行号(Amazon order-item-code,SKU 级利润归集键,对应 shop_order_item.platform_order_item_id) */
        private String orderItemId;
        /** 平台 SKU(报告原文,映射本地 SKU 走绑定关系联查,禁解析器猜) */
        private String sku;
        /** 平台原始事件类型(transaction-type,留审计与映射演进依据) */
        private String transactionType;
        /** 归一费用类型(禁 null,未归一落 OTHER) */
        private FeeType feeType;
        /** 金额(报告原值带符号:正=收入/负=费用,禁取绝对值) */
        private BigDecimal amount;
        /** 记账时间(报告 posted-date-time,缺省回落 posted-date) */
        private Instant postedAt;
        /** 数量(quantity-purchased,非订单行为 null) */
        private Integer quantity;
    }
}
