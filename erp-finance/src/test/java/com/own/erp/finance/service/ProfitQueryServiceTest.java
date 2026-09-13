package com.own.erp.finance.service;

import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitRow;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.contract.ProfitDailyTrendRow;
import com.own.erp.contract.ProfitSkuRankRow;
import com.own.erp.contract.QueryPage;
import com.own.erp.finance.entity.PlatformFeeRate;
import com.own.erp.finance.mapper.FirstLegQueryMapper;
import com.own.erp.finance.mapper.ProfitQueryMapper;
import com.own.erp.finance.profit.OrderProfitAmountGroup;
import com.own.erp.finance.profit.OrderProfitLine;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private PlatformFeeRateService platformFeeRateService;
    private FirstLegQueryMapper firstLegQueryMapper;
    private ProfitQueryService profitQueryService;

    private static final LocalDateTime ORDER_TIME = LocalDateTime.of(2026, 9, 1, 10, 0);

    @BeforeEach
    void setUp() {
        profitQueryMapper = mock(ProfitQueryMapper.class);
        exchangeRateService = mock(ExchangeRateService.class);
        platformFeeRateService = mock(PlatformFeeRateService.class);
        firstLegQueryMapper = mock(FirstLegQueryMapper.class);
        // Mockito 默认对 List 返回空表 = 费率表无数据,既有"无费率不猜"用例无需逐桩
        profitQueryService = new ProfitQueryService(profitQueryMapper, exchangeRateService, platformFeeRateService,
                firstLegQueryMapper);
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
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            QueryPage<OrderProfitRow> page = profitQueryService.page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null));

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
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().get(0);

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
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().get(0);

            assertTrue(row.commissionMissing());
            // 毛利 = 725 − 72.5 = 652.5
            assertEquals(0, new BigDecimal("652.50").compareTo(row.profitCny()));
        }

        @Test
        void estimatedCommissionWhenFeeRateConfigured() {
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(null, 501L, null, new BigDecimal("72.50"))));
            // 全站点 COMMISSION 费率 15%,8/1 生效长期
            when(platformFeeRateService.listGlobalRates("COMMISSION")).thenReturn(List.of(
                    PlatformFeeRate.builder().platform("AMAZON").feeType("COMMISSION")
                            .rate(new BigDecimal("0.150000")).effFrom(LocalDate.of(2026, 8, 1)).build()));
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().get(0);

            // 估算佣金 = −725×15% = −108.75;标志置位;利润 = 725 − 72.5 − 108.75 = 543.75
            assertEquals(0, new BigDecimal("-108.75").compareTo(row.commissionCny()));
            assertTrue(row.commissionEstimated());
            assertTrue(row.commissionMissing());
            assertEquals(0, new BigDecimal("543.75").compareTo(row.profitCny()));
        }

        @Test
        void futureFeeRateNotAppliedAndActualCommissionWins() {
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(null, 501L, null, new BigDecimal("72.50"))));
            // 9/15 才生效,下单日 9/1 取不到 → 不估算
            when(platformFeeRateService.listGlobalRates("COMMISSION")).thenReturn(List.of(
                    PlatformFeeRate.builder().platform("AMAZON").feeType("COMMISSION")
                            .rate(new BigDecimal("0.150000")).effFrom(LocalDate.of(2026, 9, 15)).build()));
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().get(0);

            assertNull(row.commissionCny());
            assertTrue(row.commissionMissing());
            assertTrue(!row.commissionEstimated());
            // 无佣可扣退化为毛利 652.50
            assertEquals(0, new BigDecimal("652.50").compareTo(row.profitCny()));
        }

        @Test
        void actualCommissionTakesPrecedenceOverEstimate() {
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(null, 501L, null, new BigDecimal("72.50"))));
            when(profitQueryMapper.sumCommissionByPlatformItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(1L, null, "AMI-1", new BigDecimal("-10.88"))));
            when(platformFeeRateService.listGlobalRates("COMMISSION")).thenReturn(List.of(
                    PlatformFeeRate.builder().platform("AMAZON").feeType("COMMISSION")
                            .rate(new BigDecimal("0.300000")).effFrom(LocalDate.of(2026, 8, 1)).build()));
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().get(0);

            // 实际佣金 −10.88 胜出,不用 30% 估算
            assertEquals(0, new BigDecimal("-10.88").compareTo(row.commissionCny()));
            assertTrue(!row.commissionEstimated());
            assertTrue(!row.commissionMissing());
        }

        @Test
        void missingRateKeepsSalesNull() {
            when(exchangeRateService.resolveRate("USD", ORDER_TIME)).thenReturn(null);
            stubEmptyAggregates();
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().get(0);

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
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, null, "CNY", "500"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().get(0);

            assertEquals(0, new BigDecimal("500.00").compareTo(row.salesCny()));
        }

        @Test
        void commissionKeyedByShopAndPlatformItemId() {
            // 跨店铺同平台行号不串行:归集键 = shopId|platformOrderItemId
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCommissionByPlatformItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(99L, null, "AMI-1", new BigDecimal("-5.00"))));
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            OrderProfitRow row = profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().get(0);

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
            when(profitQueryMapper.selectProfitLinesAll(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            line(501L, 1L, "AMI-1", "USD", "100"),
                            line(502L, 1L, "AMI-2", "USD", "200"),
                            line(503L, 1L, "AMI-3", "EUR", "300")));

            OrderProfitSummary summary = profitQueryService
                    .summarize(new OrderProfitQuery(null, null, null, null, null, 1, 50, null));

            assertEquals(3, summary.orderItemCount());
            // sales:100×7.25 + 200×7.25 = 2175(EUR 行缺汇率不计)
            assertEquals(0, new BigDecimal("2175.00").compareTo(summary.salesCny()));
            assertEquals(0, new BigDecimal("72.50").compareTo(summary.costCny()));
            assertEquals(0, new BigDecimal("-10.88").compareTo(summary.commissionCny()));
            assertEquals(1, summary.missingRateCount());
            assertEquals(2, summary.costMissingCount());
            assertEquals(2, summary.commissionMissingCount());
            // 毛利率(#21 后端下发):profit 641.62 / sales 2175 ×100 = 29.5(1 位小数 HALF_UP)
            assertEquals(0, new BigDecimal("29.5").compareTo(summary.grossMarginRate()));
        }
    }

    @Nested
    class TrendAndRank {

        /** 可变 SKU/时间/金额 的行(趋势与排行测试用) */
        private OrderProfitLine flexLine(Long orderItemId, Long skuId, String productName,
                                         LocalDateTime orderTime, String currency, String itemAmount) {
            return new OrderProfitLine(orderItemId, 1L, "PO-" + orderItemId, "AMAZON", orderTime, 1L,
                    "AMI-" + orderItemId, "SKU-X", productName, skuId, 3,
                    new BigDecimal(itemAmount), currency);
        }

        @Test
        void trendGroupsByDateAscending() {
            // 跨多日:汇率桩不限时刻(flexLine 三个下单日都要折算)
            when(exchangeRateService.resolveRate(eq("USD"), any())).thenReturn(new BigDecimal("7.25"));
            stubEmptyAggregates();
            LocalDateTime d1 = LocalDateTime.of(2026, 9, 1, 10, 0);
            LocalDateTime d2 = LocalDateTime.of(2026, 9, 2, 11, 0);
            when(profitQueryMapper.selectProfitLinesAll(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            flexLine(501L, 11L, "商品A", d2, "USD", "100"),
                            flexLine(502L, 12L, "商品B", d1, "USD", "200"),
                            flexLine(503L, 11L, "商品A", d1, "USD", "300")));

            List<ProfitDailyTrendRow> trend = profitQueryService
                    .listDailyTrend(new OrderProfitQuery(null, null, null, null, null, 1, 50, null));

            assertEquals(2, trend.size());
            assertEquals(d1.toLocalDate(), trend.get(0).statDate());
            assertEquals(d2.toLocalDate(), trend.get(1).statDate());
            assertEquals(2, trend.get(0).orderItemCount());
            // 9/1: (200+300)×7.25 = 3625
            assertEquals(0, new BigDecimal("3625.00").compareTo(trend.get(0).salesCny()));
            // 9/2: 100×7.25 = 725
            assertEquals(0, new BigDecimal("725.00").compareTo(trend.get(1).salesCny()));
        }

        @Test
        void trendSkipsGapRowsInSumsButCountsLines() {
            stubRate("USD", "7.25");
            when(exchangeRateService.resolveRate("EUR", ORDER_TIME)).thenReturn(null);
            stubEmptyAggregates();
            when(profitQueryMapper.selectProfitLinesAll(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            flexLine(501L, 11L, "商品A", ORDER_TIME, "USD", "100"),
                            flexLine(502L, 11L, "商品A", ORDER_TIME, "EUR", "999")));

            ProfitDailyTrendRow day = profitQueryService
                    .listDailyTrend(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).get(0);

            // 行数照计,金额只算有汇率的行(缺口不静默归零)
            assertEquals(2, day.orderItemCount());
            assertEquals(0, new BigDecimal("725.00").compareTo(day.salesCny()));
            assertEquals(0, BigDecimal.ZERO.compareTo(day.profitCny()));
            // 毛利率:profit 0 / sales 725 → 0.0(sales>0 不踩 NULL 禁猜)
            assertEquals(0, BigDecimal.ZERO.compareTo(day.grossMarginRate()));
        }

        @Test
        void skuRankSortsByProfitAndExcludesUnbound() {
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(null, 502L, null, new BigDecimal("700.00"))));
            // 501 无成本→利润 NULL;502 有成本→利润 1450−700=750;503 无成本且未绑定 SKU→不参与
            when(profitQueryMapper.selectProfitLinesAll(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            flexLine(501L, 11L, "商品A", ORDER_TIME, "USD", "100"),
                            flexLine(502L, 12L, "商品B", ORDER_TIME, "USD", "200"),
                            flexLine(503L, null, "未绑定行", ORDER_TIME, "USD", "300")));

            List<ProfitSkuRankRow> rank = profitQueryService
                    .listSkuProfitRank(new OrderProfitQuery(null, null, null, null, null, 1, 50, null), 10);

            assertEquals(2, rank.size());
            assertEquals(12L, rank.get(0).skuId());
            assertEquals(0, new BigDecimal("750.00").compareTo(rank.get(0).profitCny()));
            // 毛利率(#21 后端下发):750 / 1450 ×100 = 51.7
            assertEquals(0, new BigDecimal("51.7").compareTo(rank.get(0).grossMarginRate()));
            // 单行 quantity=3(聚合行数与数量语义不同)
            assertEquals(3, rank.get(0).quantity());
            assertEquals("商品B", rank.get(0).productName());
        }

        @Test
        void skuRankEnrichesFirstLegThirdLayer() {
            // #33 第三层 enrichment(方案 B 整窗摊入):批量合计按排名 SKU 集合取,无分摊 SKU 按 0 兜底
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.sumCostByOrderItemIds(anyList())).thenReturn(List.of(
                    new OrderProfitAmountGroup(null, 501L, null, new BigDecimal("25.00")),
                    new OrderProfitAmountGroup(null, 502L, null, new BigDecimal("700.00"))));
            when(profitQueryMapper.selectProfitLinesAll(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            flexLine(501L, 11L, "商品A", ORDER_TIME, "USD", "100"),
                            flexLine(502L, 12L, "商品B", ORDER_TIME, "USD", "200")));
            when(firstLegQueryMapper.sumAllocBySkuIds(any(), any(), any())).thenReturn(List.of(
                    new com.own.erp.finance.response.FirstLegSkuAllocSumRow() {{
                        setSkuId(12L);
                        setAllocAmountCny(new BigDecimal("100.00"));
                    }}));

            List<ProfitSkuRankRow> rank = profitQueryService
                    .listSkuProfitRank(new OrderProfitQuery(null, null, null, null, null, 1, 50, null), 10);

            // sku12:profit 1450−700=750,头程 100,含头程利润 650;sku11:profit 725−25=700,无分摊净利=原利润
            ProfitSkuRankRow sku12 = rank.stream().filter(r -> r.skuId() == 12L).findFirst().orElseThrow();
            ProfitSkuRankRow sku11 = rank.stream().filter(r -> r.skuId() == 11L).findFirst().orElseThrow();
            assertEquals(0, new BigDecimal("100.00").compareTo(sku12.firstLegCny()));
            assertEquals(0, new BigDecimal("650.00").compareTo(sku12.netProfitCny()));
            assertEquals(0, BigDecimal.ZERO.compareTo(sku11.firstLegCny()));
            assertEquals(0, new BigDecimal("700.00").compareTo(sku11.netProfitCny()));
        }

        @Test
        void skuRankClampsTopN() {
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.selectProfitLinesAll(any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            flexLine(501L, 11L, "商品A", ORDER_TIME, "USD", "100"),
                            flexLine(502L, 12L, "商品B", ORDER_TIME, "USD", "200"),
                            flexLine(503L, 13L, "商品C", ORDER_TIME, "USD", "300")));

            // topN=0 钳到 1;非法大值钳到 100(此处仅验下钳)
            List<ProfitSkuRankRow> rank = profitQueryService
                    .listSkuProfitRank(new OrderProfitQuery(null, null, null, null, null, 1, 50, null), 0);

            assertEquals(1, rank.size());
        }
    }

    @Nested
    class DataScope {

        @Test
        void emptyShopScopeShortCircuitsAllQueriesWithoutTouchingMapper() {
            // 数据权限(#27①):空授权集 = 不可见任何店铺,四方法短路零结果且不触库
            OrderProfitQuery query = new OrderProfitQuery(null, null, null, null, null, 1, 50, List.of());

            QueryPage<OrderProfitRow> page = profitQueryService.page(query);
            OrderProfitSummary summary = profitQueryService.summarize(query);
            List<ProfitDailyTrendRow> trend = profitQueryService.listDailyTrend(query);
            List<ProfitSkuRankRow> rank = profitQueryService.listSkuProfitRank(query, 10);

            assertEquals(0, page.total());
            assertTrue(page.list().isEmpty());
            assertEquals(0, summary.orderItemCount());
            assertEquals(0, summary.missingRateCount());
            assertNull(summary.grossMarginRate(), "空授权集短路:零行 sales=0,毛利率 NULL 禁猜");
            assertTrue(trend.isEmpty());
            assertTrue(rank.isEmpty());
            verifyNoInteractions(profitQueryMapper);
        }

        @Test
        void nullShopScopeDoesNotFilter() {
            // null = 不限(admin / 系统内部链路),走既有查询路径
            stubRate("USD", "7.25");
            stubEmptyAggregates();
            when(profitQueryMapper.selectProfitLines(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(pageOf(List.of(line(501L, 1L, "AMI-1", "USD", "100"))));

            assertEquals(1, profitQueryService
                    .page(new OrderProfitQuery(null, null, null, null, null, 1, 50, null)).list().size());
        }
    }

    /** MP Page 桩:仅 records/total 参与组装语义 */
    private Page<OrderProfitLine> pageOf(List<OrderProfitLine> lines) {
        Page<OrderProfitLine> page = new Page<>(1, 50, lines.size());
        page.setRecords(lines);
        return page;
    }
}
