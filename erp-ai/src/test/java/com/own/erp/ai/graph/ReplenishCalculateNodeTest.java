package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : ReplenishCalculateNode 单测(#6,AIR:mock 计算组件):
 *     节点职责 = 状态提取 + runtime 参数透传(#18 每轮取值);
 *     公式语义收口 ReplenishCalculatorTest
 */
class ReplenishCalculateNodeTest {

    private ReplenishCalculator calculator;
    private AiRuntimeProperties runtime;
    private ReplenishCalculateNode node;

    @BeforeEach
    void setUp() {
        calculator = mock(ReplenishCalculator.class);
        runtime = RuntimePropsStub.of(new ErpAiProperties());
        node = new ReplenishCalculateNode(calculator, runtime);
    }

    private ReplenishItem item(Long skuId, int available, int transit) {
        return ReplenishItem.builder().skuId(skuId).qtyAvailable(available).qtyTransit(transit)
                .suggestQty(0).summary("").build();
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
    void delegatesToCalculatorWithRuntimeParams() throws Exception {
        when(calculator.calculate(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(item(1L, 0, 0)));

        Map<String, Object> result = node.apply(stateOf(item(1L, 5, 5)));

        // 默认配置:window=30/coverage=14/min=10
        verify(calculator).calculate(List.of(item(1L, 5, 5)), 30, 14, 10);
        List<ReplenishItem> items = (List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS);
        assertEquals(1, items.size());
        assertEquals(0, items.get(0).qtyAvailable());
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingItemsKeyYieldsEmptyResult() throws Exception {
        when(calculator.calculate(any(), anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        Map<String, Object> result = node.apply(new OverAllState());

        assertTrue(((List<ReplenishItem>) result.get(ReplenishStateKeys.KEY_ITEMS)).isEmpty());
    }
}
