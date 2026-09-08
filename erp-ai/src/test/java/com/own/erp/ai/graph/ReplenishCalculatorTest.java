package com.own.erp.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.contract.SalesQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : ReplenishCalculator V2 单测(#6 补货算法升级,(s,S) 策略 + 安全库存,
 *     AIR:mock SalesQueryApi.listDailyQtyBySku 逐日序列):
 *     SS=ceil(z·σ·√LT)/ROP=ceil(μ·LT)+SS/S=ceil(μ·(LT+覆盖))+SS,
 *     触发=库存位置≤ROP,建议量=S−IP 下限兜底;零动销剔除、库存位置高于补货点剔除;
 *     服务水平档位最近邻映射、单日窗口 σ 退化 0、算法明细 calcJson 回填
 *     (默认参数:window=30/coverage=14/min=10/leadTime=7/serviceLevel=0.95,z=1.6449)
 */
class ReplenishCalculatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SalesQueryApi salesQueryApi;
    private ReplenishCalculator calculator;

    @BeforeEach
    void setUp() {
        salesQueryApi = mock(SalesQueryApi.class);
        calculator = new ReplenishCalculator(salesQueryApi);
    }

    private ReplenishItem item(Long skuId, int available, int transit) {
        return ReplenishItem.builder().skuId(skuId).qtyAvailable(available).qtyTransit(transit)
                .suggestQty(0).summary("").calcJson("").build();
    }

    /** 逐日序列桩(窗口内日期值本身无关紧要,统计只用数值与窗口天数) */
    private void stubSeries(long skuId, int... dailyQty) {
        Map<LocalDate, Integer> series = new java.util.HashMap<>();
        for (int i = 0; i < dailyQty.length; i++) {
            series.put(LocalDate.of(2026, 9, 1).plusDays(i), dailyQty[i]);
        }
        when(salesQueryApi.listDailyQtyBySku(any(), anyInt())).thenReturn(Map.of(skuId, series));
    }

    @Test
    void steadyDemandHasZeroSafetyStock() throws Exception {
        // 稳定需求 [3,3,3,3](4 天窗):μ=3,σ=0 → SS=0;ROP=ceil(3×7)=21,S=ceil(3×21)=63;
        // 库存位置 12 ≤ 21 触发,建议 = 63−12 = 51
        stubSeries(1L, 3, 3, 3, 3);

        List<ReplenishItem> items = calculator.calculate(
                List.of(item(1L, 10, 2)), 4, 14, 10, 7, new BigDecimal("0.95"));

        assertEquals(1, items.size());
        assertEquals(51, items.get(0).suggestQty());
        JsonNode payload = JSON.readTree(items.get(0).calcJson());
        assertEquals(0, payload.get("safetyStock").asInt());
        assertEquals(21, payload.get("reorderPoint").asInt());
        assertEquals(63, payload.get("targetQty").asInt());
    }

    @Test
    void volatileDemandAddsSafetyStock() throws Exception {
        // 波动需求 [0,6,0,6]:μ=3,σ=√12≈3.4641;SS=ceil(1.6449×3.4641×√7)=16;
        // ROP=21+16=37,S=63+16=79,库存位置 10 → 建议 69(均值公式给不出这层安全垫)
        stubSeries(1L, 0, 6, 0, 6);

        List<ReplenishItem> items = calculator.calculate(
                List.of(item(1L, 10, 0)), 4, 14, 10, 7, new BigDecimal("0.95"));

        assertEquals(1, items.size());
        assertEquals(69, items.get(0).suggestQty());
        JsonNode payload = JSON.readTree(items.get(0).calcJson());
        assertEquals("REORDER_POINT_V2", payload.get("algorithm").asText());
        assertEquals(16, payload.get("safetyStock").asInt());
        assertEquals(37, payload.get("reorderPoint").asInt());
        assertEquals(79, payload.get("targetQty").asInt());
        assertEquals(69, payload.get("suggestQty").asInt());
        assertEquals(3.0, payload.get("avgDaily").asDouble(), 1e-9);
    }

    @Test
    void inventoryAboveReorderPointDropped() throws Exception {
        // 库存位置 50 > 补货点 37:提前期内不断货,不产建议(V1.5 均值公式在此仍会建议)
        stubSeries(1L, 0, 6, 0, 6);

        List<ReplenishItem> items = calculator.calculate(
                List.of(item(1L, 50, 0)), 4, 14, 10, 7, new BigDecimal("0.95"));

        assertTrue(items.isEmpty());
    }

    @Test
    void zeroSalesDeadSkuDropped() {
        // 零动销(死 SKU):μ=0 ⇒ σ=0 ⇒ ROP=0,不硬补(2026-09-07 拍板语义 V2 保持)
        when(salesQueryApi.listDailyQtyBySku(any(), anyInt())).thenReturn(Map.of());

        List<ReplenishItem> items = calculator.calculate(
                List.of(item(1L, 3, 0)), 30, 14, 10, 7, new BigDecimal("0.95"));

        assertTrue(items.isEmpty());
    }

    @Test
    void minSuggestQtyFloorsSmallNeeds() {
        // 低动销 30 天 3 件(每 10 天 1 件):μ=0.1,σ≈0.3051,SS=2,ROP=3,S=5;
        // 库存位置 3 ≤ 3 触发,缺口 2 < 下限 10 → 下限兜底
        Map<LocalDate, Integer> series = new java.util.HashMap<>();
        for (int i = 0; i < 30; i++) {
            if (i % 10 == 0) {
                series.put(LocalDate.of(2026, 8, 10).plusDays(i), 1);
            }
        }
        when(salesQueryApi.listDailyQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, series));

        List<ReplenishItem> items = calculator.calculate(
                List.of(item(1L, 3, 0)), 30, 14, 10, 7, new BigDecimal("0.95"));

        assertEquals(1, items.size());
        assertEquals(10, items.get(0).suggestQty());
    }

    @Test
    void singleDayWindowDegeneratesToZeroSigma() {
        // 窗口 1 天:σ 退化 0(n−1=0),公式退化为纯均值口径:ROP=35,S=105,缺货建议 105
        stubSeries(1L, 5);

        List<ReplenishItem> items = calculator.calculate(
                List.of(item(1L, 0, 0)), 1, 14, 10, 7, new BigDecimal("0.95"));

        assertEquals(1, items.size());
        assertEquals(105, items.get(0).suggestQty());
    }

    @Test
    void emptyItemsSkipsSalesQuery() {
        List<ReplenishItem> items = calculator.calculate(
                List.of(), 30, 14, 10, 7, new BigDecimal("0.95"));

        assertTrue(items.isEmpty());
        verifyNoInteractions(salesQueryApi);
    }

    @Test
    void serviceLevelMapsToNearestKnownTier() {
        // 档位最近邻:0.93→0.95 档 1.6449;0.97→0.98 档 2.0537;0.89→0.90 档 1.2816;
        // 0.99 档 2.3263;null 回落 0.95 档(与配置默认一致)
        assertEquals(1.6449, ReplenishCalculator.zOf(new BigDecimal("0.93")));
        assertEquals(2.0537, ReplenishCalculator.zOf(new BigDecimal("0.97")));
        assertEquals(1.2816, ReplenishCalculator.zOf(new BigDecimal("0.89")));
        assertEquals(2.3263, ReplenishCalculator.zOf(new BigDecimal("0.99")));
        assertEquals(1.6449, ReplenishCalculator.zOf(null));
    }
}
