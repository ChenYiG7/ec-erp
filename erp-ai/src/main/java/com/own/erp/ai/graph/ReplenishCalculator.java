package com.own.erp.ai.graph;

import com.own.erp.contract.SalesQueryApi;
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
 * @Date : 2026/9/8
 * @Description : 补货量计算器(#6 销量数据面重估公式,#17 采购建议工作流复用,
 *     2026-09-08 自 ReplenishCalculateNode 抽取——公式单一来源,两工作流组合复用):
 *     纯程序规则算建议补货量,不调模型(铁律 8:判断归 AI、执行归程序——公式确定性强、零 token)。
 *     公式:日均销量 = 动销窗口内真实销量合计/窗口天数(SalesQueryApi 读 order_sales_daily);
 *     建议量 = 覆盖天数 × 日均销量 − 可用 − 在途;≤0 不建议(有货且动销跟得上/窗口内零动销的
 *     死 SKU 不硬补);>0 时按最小建议量下限兜底。参数由调用方传入(#18 系统设置口径:
 *     运行参数走 AiRuntimeProperties 每轮取值,组件无状态可复用)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplenishCalculator {

    private final @Lazy SalesQueryApi salesQueryApi;

    /** 建议量计算:回填 suggestQty,剔除不需要补货的 SKU(返回新列表,不改入参) */
    public List<ReplenishItem> calculate(List<ReplenishItem> items, int salesWindowDays,
                                         int coverageDays, int minSuggestQty) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> soldBySku = salesQueryApi.sumQtyBySku(
                items.stream().map(ReplenishItem::skuId).collect(Collectors.toSet()),
                salesWindowDays);
        List<ReplenishItem> calculated = new ArrayList<>(items.size());
        int skipped = 0;
        for (ReplenishItem item : items) {
            int sold = soldBySku.getOrDefault(item.skuId(), 0);
            // 覆盖需求 = 覆盖天数×日均销量(窗口合计折算,日均值不在中间步骤截断)
            long demand = (long) Math.ceil((double) coverageDays * sold / salesWindowDays);
            long need = demand - item.qtyAvailable() - item.qtyTransit();
            if (need <= 0) {
                // 有货且动销跟得上,或窗口内零动销(死 SKU 不硬补):不产建议
                skipped++;
                continue;
            }
            calculated.add(item.toBuilder()
                    .suggestQty((int) Math.min(Integer.MAX_VALUE,
                            Math.max(minSuggestQty, need)))
                    .build());
        }
        log.info("补货量计算完成:候选 {} 个,建议 {} 个,不需要补货剔除 {} 个",
                items.size(), calculated.size(), skipped);
        return calculated;
    }
}
