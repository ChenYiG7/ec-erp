package com.own.erp.finance.profit;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 结算侧费种聚合行(#19 周期口径):单报告 settlement_detail 按 fee_type 分组的原币合计,
 *     金额报告原值带符号(TRANSFER 为正、COMMISSION/FBA 系为负);折算与费种归类在 Service 层,
 *     SQL 不做币种换算(同 ProfitQueryMapper 口径)
 */
public record SettlementFeeSum(

        /** 费用类型(UnifiedSettlement.FeeType 词表:SALE/REFUND/COMMISSION/FBA_FEE/STORAGE/ADVERTISING/TRANSFER/OTHER) */
        String feeType,

        /** 该费种原币合计(带符号) */
        BigDecimal amount
) {
}
