package com.own.erp.finance.service;

import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitRow;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.contract.QueryPage;
import com.own.erp.finance.mapper.ProfitQueryMapper;
import com.own.erp.finance.profit.OrderProfitAmountGroup;
import com.own.erp.finance.profit.OrderProfitLine;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : ProfitQueryService 单测(AIR:mock mapper/汇率服务,不依赖数据库;#19③ 组装口径必测)。
 *     必测面:三金额折算与利润公式(佣金带符号负数)/缺汇率缺成本缺佣金三缺口语义(禁静默归零)/
 *     佣金按 店铺+平台行号 归集/汇总缺口计数
 */
class ProfitQueryServiceTest {

    private ProfitQueryMapper profitQueryMapper;
    private ExchangeRateService exchangeRateService;
    private ProfitQueryService profitQueryService;

    private static final LocalDateTime ORDER_TIME = LocalDateTime.of(2026, 9, 1, 10, 0);

    @BeforeEach
    void setUp() {
        profitQueryMapper = mock(ProfitQueryMapper.class);
        exchangeRateService = mock(ExchangeRateService.class);
        profitQueryService = new ProfitQueryService(profitQueryMapper, exchangeRateService);
    }

    /** 主查询行:参数见名 */
    private OrderProfitLine line(Long orderItemId, Long shopId, String platformOrderItemId,
                                 String currency, String itemAmount) {
        return new OrderProfitLine(orderItemId, 1L, "PO-1", "AMAZON", ORDER_TIME, shopId,
                platformOrderItemId, "SKU-A", "商品A", 11L, 2, new BigDecimal(itemAmount), currency);
    }

    private void stubRate(String currency, String rate) {
        when(exchangeRateService.resolveRate(currency, ORDER_TIME)).thenReturn(new BigDecimal(rate));
    }

    private void stubEmptyAggregates() {
        when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of());
        when(profitQueryMapper.sumCommissionByPlatformItemIds(anyList())).thenReturn(List.of());
    }

    @Nested
    class Assemble {

        @Test
        void fullProfitChainWithSignedCommission() {
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(null, 501L, null, new BigDecimal("72.50"))));
            when(profitQueryMapper.sumCommissionByPlatformItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(1L, null, "AMI-1", new BigDecimal("-10.88"))));
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            QueryPage<OrderProfitRow> page = profitQueryService.page(new OrderProfitQuery(null, null, null, null, null, 1, 50));

            OrderProfitRow row = page.list().get(0);
            assertEquals(0, new BigDecimal("725.00").compareTo(row.salesCny()));
            assertEquals(0, new BigDecimal("72.50").compareTo(row.costCny()));
            assertEquals(0, new BigDecimal("-10.88").compareTo(row.commissionCny()));
            // 利润 = 725 − 72.5 + (−10.88) = 641.62
            assertEquals(0, new BigDecimal("641.62").compareTo(row.profitCny()));
            assertTrue(!row.costMissing() && !row.commissionMissing());
            assertEquals(1, page.total());
        }

        @Test
        void missingCostKeepsProfitNull() {
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50)).list().get(0);

            assertNull(row.costCny());
            assertTrue(row.costMissing());
            assertNull(row.profitCny());
        }

        @Test
        void missingCommissionDegradesToGrossProfit() {
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(null, 501L, null, new BigDecimal("72.50"))));
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50)).list().get(0);

            assertTrue(row.commissionMissing());
            // 毛利 = 725 − 72.5 = 652.5
            assertEquals(0, new BigDecimal("652.50").compareTo(row.profitCny()));
        }

        @Test
        void missingRateKeepsSalesNull() {
            when(exchangeRateService.resolveRate("USD", ORDER_TIME)).thenReturn(null);
            stubEmptyAggregates();
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50)).list().get(0);

            assertNull(row.rate());
            assertNull(row.salesCny());
            assertNull(row.profitCny());
        }

        @Test
        void cnyOrderWithoutRateRowStillConverts() {
            // CNY 短路=1 在汇率服务内(此处服务为 mock,折算路径由 ExchangeRateServiceTest 覆盖);
            // 本测验证汇率→本位币乘法的确定性
            stubRate("CNY", "1");
            stubEmptyAggregates();
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, null, "CNY", "500"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50)).list().get(0);

            assertEquals(0, new BigDecimal("500.00").compareTo(row.salesCny()));
        }

        @Test
        void commissionKeyedByShopAndPlatformItemId() {
            // 跨店铺同平台行号不串行:归集键 = shopId|platformOrderItemId
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCommissionByPlatformItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(99L, null, "AMI-1", new BigDecimal("-5.00"))));
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50)).list().get(0);

            // 佣金归集在别店(99),本店(1)视为待结算
            assertTrue(row.commissionMissing());
        }
    }

    @Nested
    class Summarize {

        @Test
        void countsGapsSeparatelyWithoutSilentZero() {
            stubRate("USD", "7.25");
            when(exchangeRateService.resolveRate("EUR", ORDER_TIME)).thenReturn(null);
            stubEmptyAggregates();
            when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(null, 501L, null, new BigDecimal("72.50"))));
            when(profitQueryMapper.sumCommissionByPlatformItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(1L, null, "AMI-1", new BigDecimal("-10.88"))));
            // 三行:齐备(USD)/缺佣金(USD 未出库也无佣金→缺成本)/缺汇率(EUR)
            when(profitQueryMapper.selectProfitLinesAll(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            line(501L, 1L, "AMI-1", "USD", "100"),
                            line(502L, 1L, "AMI-2", "USD", "200"),
                            line(503L, 1L, "AMI-3", "EUR", "300")));

            OrderProfitSummary summary = profitQueryService
                    .summarize(new OrderProfitQuery(null, null, null, null, null, 1, 50));

            assertEquals(3, summary.orderItemCount());
            // sales:100×7.25 + 200×7.25 = 2175(EUR 行缺汇率不计)
            assertEquals(0, new BigDecimal("2175.00").compareTo(summary.salesCny()));
            assertEquals(0, new BigDecimal("72.50").compareTo(summary.costCny()));
            assertEquals(0, new BigDecimal("-10.88").compareTo(summary.commissionCny()));
            assertEquals(1, summary.missingRateCount());
            assertEquals(2, summary.costMissingCount());
            assertEquals(2, summary.commissionMissingCount());
        }
    }

    /** MP Page 桩:仅 records/total 参与组装语义 */
    private Page<OrderProfitLine> pageOf(List<OrderProfitLine> lines) {
        Page<OrderProfitLine> page = new Page<>(1, 50, lines.size());
        page.setRecords(lines);
        return page;
    }
}
