package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 取数节点(#6 SAA Graph 补货工作流,拍板口径:程序取数,LLM 只写报告):
 *     扫描口径(2026-09-08 抽取)收口共享组件 LowStockScanner——补货/采购两工作流单一来源;
 *     去重(2026-09-07 拍板):同 SKU 存在待确认(status=0)建议即跳过,收口在计算/摘要前——零浪费 token;
 *     旧建议被采纳/忽略后若库存仍低允许再产出,确认闭环自然运转。
 *     扫描护栏(页大小/行数上限)走 erp.ai.replenish.* 配置;阈值走 runtime(#18 系统设置热更)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplenishCollectNode implements NodeAction {

    private final LowStockScanner scanner;
    private final ErpAiProperties props;
    private final AiRuntimeProperties runtime;
    private final AiSuggestionService aiSuggestionService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 参数每轮取值(#18 系统设置):DB 覆盖值优先,yml/代码默认兜底,热更即时生效
        int lowStockThreshold = runtime.replenishLowStockThreshold();
        LowStockScanner.ScanResult scan = scanner.scan(lowStockThreshold,
                props.getReplenish().getScanPageSize(), props.getReplenish().getScanMaxRows());
        List<ReplenishItem> items = new ArrayList<>(scan.items());
        int deduped = dedupPending(items);
        log.info("补货取数完成:扫描 {} 行,低库存 SKU {} 个(阈值≤{}),去重跳过 {} 个",
                scan.scanned(), items.size(), lowStockThreshold, deduped);
        return Map.of(ReplenishStateKeys.KEY_ITEMS, items, ReplenishStateKeys.KEY_SCANNED, scan.scanned());
    }

    /** 同 SKU 已存在待确认建议则移除(定时/手动统一口径),返回移除数;无命中不查库 */
    private int dedupPending(List<ReplenishItem> items) {
        if (items.isEmpty()) {
            return 0;
        }
        Set<Long> pending = aiSuggestionService.findPendingSkuIds(AiConsts.TYPE_REPLENISH);
        int before = items.size();
        items.removeIf(item -> pending.contains(item.skuId()));
        return before - items.size();
    }
}
