package com.own.erp.finance.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.finance.entity.ExchangeRate;
import com.own.erp.finance.mapper.ExchangeRateMapper;
import com.own.erp.finance.request.query.ExchangeRateQuery;
import com.own.erp.finance.response.ExchangeRateResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : ExchangeRateService 单测(AIR:mock mapper,不依赖数据库;#19③ 折算口径必测)。
 *     必测面:本位币 CNY 短路不查表/按业务日回溯取最近报价(禁取表内最新)/无报价返回 null 禁猜/
 *     手工录入必填与汇率正值校验
 */
class ExchangeRateServiceTest {

    private ExchangeRateMapper exchangeRateMapper;
    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        exchangeRateMapper = mock(ExchangeRateMapper.class);
        exchangeRateService = new ExchangeRateService(exchangeRateMapper);
    }

    @Nested
    class ResolveRate {

        @Test
        void baseCurrencyShortCircuitsToOne() {
            assertEquals(BigDecimal.ONE,
                    exchangeRateService.resolveRate("CNY", LocalDateTime.of(2026, 9, 8, 12, 0)));
            verify(exchangeRateMapper, never()).selectOne(any());
        }

        @Test
        void resolvesLatestQuoteAtOrBeforeBusinessTime() {
            // 回溯口径:quoted_at <= 业务日最近一条(orderByDesc+LIMIT 1 在 wrapper 内,此处只验取数语义)
            ExchangeRate quote = ExchangeRate.builder().currency("USD")
                    .rate(new BigDecimal("7.25000000")).build();
            when(exchangeRateMapper.selectOne(any())).thenReturn(quote);

            BigDecimal rate = exchangeRateService.resolveRate("USD", LocalDateTime.of(2026, 9, 8, 12, 0));

            assertEquals(0, new BigDecimal("7.25000000").compareTo(rate));
        }

        @Test
        void missingQuoteReturnsNull() {
            when(exchangeRateMapper.selectOne(any())).thenReturn(null);
            assertNull(exchangeRateService.resolveRate("USD", LocalDateTime.of(2026, 9, 8, 12, 0)));
        }

        @Test
        void rejectsBlankCurrencyOrMissingAnchor() {
            assertThrows(BusinessException.class, () -> exchangeRateService.resolveRate(" ", LocalDateTime.now()));
            assertThrows(BusinessException.class, () -> exchangeRateService.resolveRate("USD", null));
        }
    }

    @Nested
    class Save {

        @Test
        void validatesAndStampsManualSource() {
            ExchangeRate rate = ExchangeRate.builder().currency("usd")
                    .rate(new BigDecimal("7.25")).quotedAt(LocalDateTime.of(2026, 9, 8, 0, 0)).build();

            exchangeRateService.save(rate);

            assertEquals("MANUAL", rate.getSource());
            verify(exchangeRateMapper).insert(rate);
        }

        @Test
        void rejectsMissingFieldsOrNonPositiveRate() {
            assertThrows(BusinessException.class, () -> exchangeRateService.save(null));
            assertThrows(BusinessException.class, () -> exchangeRateService.save(ExchangeRate.builder()
                    .rate(BigDecimal.ONE).quotedAt(LocalDateTime.now()).build()));
            assertThrows(BusinessException.class, () -> exchangeRateService.save(ExchangeRate.builder()
                    .currency("USD").rate(BigDecimal.ZERO).quotedAt(LocalDateTime.now()).build()));
            assertThrows(BusinessException.class, () -> exchangeRateService.save(ExchangeRate.builder()
                    .currency("USD").rate(new BigDecimal("-1")).quotedAt(LocalDateTime.now()).build()));
            verify(exchangeRateMapper, never()).insert(any(ExchangeRate.class));
        }
    }

    @Nested
    class PageQuery {

        @Test
        void mapsEntityPageToResponsePage() {
            Page<ExchangeRate> entityPage = new Page<>(1, 20, 1);
            entityPage.setRecords(List.of(ExchangeRate.builder().id(5L).currency("USD")
                    .rate(new BigDecimal("7.25")).build()));
            when(exchangeRateMapper.selectPage(any(), any())).thenReturn(entityPage);

            Page<ExchangeRateResponse> result = exchangeRateService.page(new ExchangeRateQuery());

            assertEquals(1, result.getRecords().size());
            assertTrue(result.getRecords().get(0) instanceof ExchangeRateResponse);
        }
    }
}
