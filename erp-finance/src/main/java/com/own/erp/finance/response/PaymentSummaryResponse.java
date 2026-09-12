package com.own.erp.finance.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 期间资金汇总(#31 资金流追踪查询面③):NORMAL 流水按 paid_at 期间汇总 CNY 口径;
 *     缺汇率行进 missingRateCount 且其金额不进收付合计(缺口计数不静默,同 #19 纪律)
 */
@Builder
public record PaymentSummaryResponse(

        /** 期间起(含,可空=不设下界) */
        LocalDateTime paidFrom,

        /** 期间止(含,可空=不设上界) */
        LocalDateTime paidTo,

        /** 期间回款合计(CNY) */
        BigDecimal incomeCny,

        /** 期间付款合计(CNY) */
        BigDecimal expenseCny,

        /** 收付净额(回款 − 付款,CNY) */
        BigDecimal netCny,

        /** 流水笔数(含缺汇率行) */
        Long totalCount,

        /** 缺汇率行数(金额未进上面 CNY 合计,前端显"折算缺失") */
        Long missingRateCount
) {
}
