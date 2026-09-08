package com.own.erp.finance.response;

import com.own.erp.finance.entity.ExchangeRate;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 汇率快照出参(docs/07 §1:出参一律 Response,实体不对外)
 */
@Data
@Builder
public class ExchangeRateResponse {

    private Long id;

    /** 币种(ISO 4217) */
    private String currency;

    /** 汇率快照(1 currency = rate CNY) */
    private BigDecimal rate;

    /** 报价时间 */
    private LocalDateTime quotedAt;

    /** MANUAL/API */
    private String source;

    private LocalDateTime createdAt;

    public static ExchangeRateResponse from(ExchangeRate entity) {
        return ExchangeRateResponse.builder()
                .id(entity.getId())
                .currency(entity.getCurrency())
                .rate(entity.getRate())
                .quotedAt(entity.getQuotedAt())
                .source(entity.getSource())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
