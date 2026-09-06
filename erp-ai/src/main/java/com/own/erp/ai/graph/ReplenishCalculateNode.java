package com.own.erp.ai.graph;

import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.contract.SalesQueryApi;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 计算节点(#6 SAA Graph 补货工作流):纯程序规则算建议补货量,不调模型(铁律 8:
 *     判断归 AI、执行归程序——公式确定性强、零 token)。公式:日均销量 = 动销窗口内真实销量合计/窗口天数
 *     (2026-09-07 重估:销量数据面 order_sales_daily 落地,弃固定估计值 assumedDailySales;
 *     读侧走 SalesQueryApi 只读契约,铁律 2);
 *     建议量 = 覆盖天数 × 日均销量 − 可用 − 在途;≤0 不建议(有货且动销跟得上/窗口内零动销的死 SKU
 *     不再硬补);>0 时按最小建议量下限兜底。参数全走 erp.ai.replenish.* 配置不硬编码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplenishCalculateNode implements NodeAction {

    private final ErpAiProperties props;
    private final @Lazy SalesQueryApi salesQueryApi;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        ErpAiProperties.Replenish cfg = props.getReplenish();
        List<ReplenishItem> items = (List<ReplenishItem>) state.value(ReplenishStateKeys.KEY_ITEMS)
                .orElse(List.of());
        if (items.isEmpty()) {
            return Map.of(ReplenishStateKeys.KEY_ITEMS, items);
        }
        Map<Long, Integer> soldBySku = salesQueryApi.sumQtyBySku(
                items.stream().map(ReplenishItem::skuId).collect(Collectors.toSet()),
                cfg.getSalesWindowDays());
        List<ReplenishItem> calculated = new ArrayList<>(items.size());
        int skipped = 0;
        for (ReplenishItem item : items) {
            int sold = soldBySku.getOrDefault(item.skuId(), 0);
            // 覆盖需求 = 覆盖天数×日均销量(窗口合计折算,日均值不在中间步骤截断)
            long demand = (long) Math.ceil((double) cfg.getCoverageDays() * sold / cfg.getSalesWindowDays());
            long need = demand - item.qtyAvailable() - item.qtyTransit();
            if (need <= 0) {
                // 有货且动销跟得上,或窗口内零动销(死 SKU 不硬补):不产建议
                skipped++;
                continue;
            }
            calculated.add(item.toBuilder()
                    .suggestQty((int) Math.min(Integer.MAX_VALUE,
                            Math.max(cfg.getMinSuggestQty(), need)))
                    .build());
        }
        log.info("补货计算完成:候选 {} 个,建议 {} 个,不需要补货剔除 {} 个",
                items.size(), calculated.size(), skipped);
        return Map.of(ReplenishStateKeys.KEY_ITEMS, calculated);
    }
}
