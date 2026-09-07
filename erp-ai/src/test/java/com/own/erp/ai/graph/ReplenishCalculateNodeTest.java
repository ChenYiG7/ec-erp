package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.contract.SalesQueryApi;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
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
 * @Date : 2026/9/7
 * @Description : ReplenishCalculateNode 单测(#6 销量数据面重估后,AIR:mock SalesQueryApi):
 *     建议量 = max(最小建议量, ceil(覆盖天数×窗口销量/窗口天数)−可用−在途);
 *     零动销/库存充足剔除不产建议、分数速率向上取整、下限兜底;OverAllState 真实装配
 */
class ReplenishCalculateNodeTest {

    private ErpAiProperties props;
    private AiRuntimeProperties runtime;
    private SalesQueryApi salesQueryApi;
    private ReplenishCalculateNode node;

    @BeforeEach
    void setUp() {
        props = new ErpAiProperties();
        runtime = RuntimePropsStub.of(props);
        // 默认配置:coverage=14, window=30, min=10
        salesQueryApi = mock(SalesQueryApi.class);
        node = new ReplenishCalculateNode(runtime, salesQueryApi);
    }

    private ReplenishItem item(Long skuId, int available, int transit) {
        return ReplenishItem.builder().skuId(skuId).qtyAvailable(available).qtyTransit(transit).build();
    }

    private OverAllState stateOf(ReplenishItem... items) {
        // 裸 OverAllState 不注册策略的键会被丢弃,直测节点须先注册(真实工作流由图装配注册)
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(ReplenishStateKeys.KEY_ITEMS, KeyStrategy.REPLACE);
        state.input(Map.of(ReplenishStateKeys.KEY_ITEMS, List.of(items)));
        return state;
    }

    @Test
    @SuppressWarnings("unchecked")
    void computesSuggestQtyWithRealSalesRate() throws Exception {
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 60, 2L, 60));

        Map<String, Object> result = node.apply(stateOf(item(1L, 5, 5), item(2L, 0, 0)));

        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        // 需求基数 ceil(14×60/30)=28:可用5 在途5 → 28-10=18;可用0 在途0 → 28
        assertEquals(18, items.get(0).suggestQty());
        assertEquals(28, items.get(1).suggestQty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fractionalSalesRateCeilsUp() throws Exception {
        // 45/30=1.5/天 → 需求 ceil(14×1.5)=21;可用3 → 建议建 18(未触下限)
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 45));

        List<ReplenishItem> items = (List<ReplenishItem>)
                node.apply(stateOf(item(1L, 3, 0))).get(ReplenishStateKeys.KEY_ITEMS);

        assertEquals(1, items.size());
        assertEquals(18, items.get(0).suggestQty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void minSuggestQtyFloorsSmallNeeds() throws Exception {
        // 15/30=0.5/天 → 需求 ceil(7)=7;可用3 → 差 4 → 下限兜底 10
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 15));

        List<ReplenishItem> items = (List<ReplenishItem>)
                node.apply(stateOf(item(1L, 3, 0))).get(ReplenishStateKeys.KEY_ITEMS);

        assertEquals(10, items.get(0).suggestQty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroSalesDeadSkuDropped() throws Exception {
        // 零动销(死 SKU)不再硬补:整条剔除,不产建议(2026-09-07 重估拍板)
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of());

        List<ReplenishItem> items = (List<ReplenishItem>)
                node.apply(stateOf(item(1L, 3, 0))).get(ReplenishStateKeys.KEY_ITEMS);

        assertTrue(items.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sufficientStockDropped() throws Exception {
        // 有货且动销跟得上:需求 ≤ 现有,不产建议
        when(salesQueryApi.sumQtyBySku(any(), anyInt())).thenReturn(Map.of(1L, 60));

        List<ReplenishItem> items = (List<ReplenishItem>)
                node.apply(stateOf(item(1L, 100, 0))).get(ReplenishStateKeys.KEY_ITEMS);

        assertTrue(items.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyItemsSkipsSalesQuery() throws Exception {
        Map<String, Object> result = node.apply(stateOf());

        assertTrue(((List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS)).isEmpty());
        verifyNoInteractions(salesQueryApi);
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingItemsKeyYieldsEmptyResult() throws Exception {
        Map<String, Object> result = node.apply(new OverAllState());

        assertEquals(0, ((List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS)).size());
    }
}
