package com.own.erp.ai.graph;

import cn.hutool.core.collection.CollUtil;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.QueryPage;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 取数节点(#6 SAA Graph 补货工作流,拍板口径:程序取数,LLM 只写报告):
 *     分页扫 inventory,可用 ≤ 阈值命中,按 skuId 跨仓合并(可用/在途求和)产出原始聚合行;
 *     去重(2026-09-07 拍板):同 SKU 存在待确认(status=0)建议即跳过,收口在计算/摘要前——零浪费 token;
 *     旧建议被采纳/忽略后若库存仍低允许再产出,确认闭环自然运转。
 *     扫描护栏(页大小/行数上限)走 erp.ai.replenish.* 配置;取数只走 InventoryQueryApi 只读契约(铁律 2/7)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplenishCollectNode implements NodeAction {

    private final @Lazy InventoryQueryApi inventoryQueryApi;
    private final ErpAiProperties props;
    private final AiRuntimeProperties runtime;
    private final AiSuggestionService aiSuggestionService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 参数每轮取值(#18 系统设置):DB 覆盖值优先,yml/代码默认兜底,热更即时生效
        int lowStockThreshold = runtime.replenishLowStockThreshold();
        int scanPageSize = props.getReplenish().getScanPageSize();
        int scanMaxRows = props.getReplenish().getScanMaxRows();
        Map<Long, ReplenishItem> mergedBySku = new LinkedHashMap<>();
        int scanned = 0;
        int pageNo = 1;
        while (scanned < scanMaxRows) {
            QueryPage<InventoryQueryApi.InventoryView> page = inventoryQueryApi.pageInventory(
                    InventoryQueryApi.InventoryFilter.builder()
                            .pageNo(pageNo).pageSize(scanPageSize).build());
            List<InventoryQueryApi.InventoryView> rows = page == null ? null : page.list();
            if (CollUtil.isEmpty(rows)) {
                break;
            }
            for (InventoryQueryApi.InventoryView row : rows) {
                if (row.skuId() == null || row.qtyAvailable() == null
                        || row.qtyAvailable() > lowStockThreshold) {
                    continue;
                }
                // 跨仓合并:同 skuId 可用/在途求和,LinkedHashMap 稳定顺序
                mergedBySku.merge(row.skuId(),
                        ReplenishItem.builder()
                                .skuId(row.skuId())
                                .qtyAvailable(row.qtyAvailable())
                                .qtyTransit(row.qtyTransit() == null ? 0 : row.qtyTransit())
                                .suggestQty(0)
                                .summary("")
                                .build(),
                        (a, b) -> ReplenishItem.builder()
                                .skuId(a.skuId())
                                .qtyAvailable(a.qtyAvailable() + b.qtyAvailable())
                                .qtyTransit(a.qtyTransit() + b.qtyTransit())
                                .suggestQty(0)
                                .summary("")
                                .build());
            }
            scanned += rows.size();
            pageNo++;
            if (rows.size() < scanPageSize) {
                break;
            }
        }
        List<ReplenishItem> items = new ArrayList<>(mergedBySku.values());
        int deduped = dedupPending(items);
        log.info("补货取数完成:扫描 {} 行,低库存 SKU {} 个(阈值≤{}),去重跳过 {} 个",
                scanned, items.size(), lowStockThreshold, deduped);
        return Map.of(ReplenishStateKeys.KEY_ITEMS, items, ReplenishStateKeys.KEY_SCANNED, scanned);
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
