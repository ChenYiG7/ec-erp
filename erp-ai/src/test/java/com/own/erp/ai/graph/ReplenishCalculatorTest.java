package com.own.erp.ai.graph;

import com.own.erp.contract.SalesQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * @Description : ReplenishCalculator 单测(#6 销量数据面重估公式,#17 采购建议复用,
 *     AIR:mock SalesQueryApi):建议量 = max(最小建议量, ceil(覆盖天数×窗口销量/窗口天数)−可用−在途);
 *     零动销/库存充足剔除不产建议、分数速率向上取整、下限兜底
 *     (2026-09-08 自 ReplenishCalculateNodeTest 迁移)
 */
class ReplenishCalculatorTest {

    private SalesQueryApi salesQueryApi;
    private ReplenishCalculator calculator;

    @BeforeEach
    void setUp() {
        salesQueryApi = mock(SalesQueryApi.class);
        // 默认配置口径:coverage=14, window=30, min=10
        calculator = new ReplenishCalculator(salesQueryApi);
    }

    private ReplenishItem item(Long skuId, int available, int transit) {
        return ReplenishItem.builder().skuId(skuId).qtyAvailable(available).qtyTransit(transit)
                .suggestQty(0).summary("").build();
    }

    @Test
    void computesSuggestQtyWithRealSalesRate() {
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 60, 2L, 60));

        List<ReplenishItem> items = calculator.calculate(
                List.of(item(1L, 5, 5), item(2L, 0, 0)), 30, 14, 10);

        // 需求基数 ceil(14×60/30)=28:可用5 在途5 → 28-10=18;可用0 在途0 → 28
        assertEquals(2, items.size());
        assertEquals(18, items.get(0).suggestQty());
        assertEquals(28, items.get(1).suggestQty());
    }

    @Test
    void fractionalSalesRateCeilsUp() {
        // 45/30=1.5/天 → 需求 ceil(14×1.5)=21;可用3 → 建议 18(未触下限)
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 45));

        List<ReplenishItem> items = calculator.calculate(List.of(item(1L, 3, 0)), 30, 14, 10);

        assertEquals(1, items.size());
        assertEquals(18, items.get(0).suggestQty());
    }

    @Test
    void minSuggestQtyFloorsSmallNeeds() {
        // 15/30=0.5/天 → 需求 ceil(7)=7;可用3 → 差 4 → 下限兜底 10
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 15));

        List<ReplenishItem> items = calculator.calculate(List.of(item(1L, 3, 0)), 30, 14, 10);

        assertEquals(10, items.get(0).suggestQty());
    }

    @Test
    void zeroSalesDeadSkuDropped() {
        // 零动销(死 SKU)不再硬补:整条剔除,不产建议(2026-09-07 重估拍板)
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of());

        List<ReplenishItem> items = calculator.calculate(List.of(item(1L, 3, 0)), 30, 14, 10);

        assertTrue(items.isEmpty());
    }

    @Test
    void sufficientStockDropped() {
        // 有货且动销跟得上:需求 ≤ 现有,不产建议
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 60));

        List<ReplenishItem> items = calculator.calculate(List.of(item(1L, 100, 0)), 30, 14, 10);

        assertTrue(items.isEmpty());
    }

    @Test
    void emptyItemsSkipsSalesQuery() {
        List<ReplenishItem> items = calculator.calculate(List.of(), 30, 14, 10);

        assertTrue(items.isEmpty());
        verifyNoInteractions(salesQueryApi);
    }
}
