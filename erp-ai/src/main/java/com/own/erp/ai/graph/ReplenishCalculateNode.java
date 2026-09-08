package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 计算节点(#6 SAA Graph 补货工作流):建议量公式(2026-09-08 抽取)
 *     收口共享组件 ReplenishCalculator——补货/采购两工作流单一来源(纯程序零 token,铁律 8);
 *     参数每轮取值(#18 系统设置):窗口/覆盖天数/下限即时生效
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplenishCalculateNode implements NodeAction {

    private final ReplenishCalculator calculator;
    private final AiRuntimeProperties runtime;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<ReplenishItem> items = (List<ReplenishItem>) state.value(ReplenishStateKeys.KEY_ITEMS)
                .orElse(List.of());
        List<ReplenishItem> calculated = calculator.calculate(items,
                runtime.replenishSalesWindowDays(), runtime.replenishCoverageDays(),
                runtime.replenishMinSuggestQty());
        return Map.of(ReplenishStateKeys.KEY_ITEMS, calculated);
    }
}
