package com.own.erp.ai.graph;

import cn.hutool.core.collection.CollUtil;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.QueryPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 低库存扫描器(#6 补货工作流与 #17 采购建议工作流的共享取数组件,
 *     2026-09-08 自 ReplenishCollectNode 抽取——扫描口径单一来源,两工作流组合复用):
 *     分页扫 inventory(可用 ≤ 阈值命中),按 skuId 跨仓合并(可用/在途求和)产出原始聚合行
 *     (suggestQty=0,建议量由 ReplenishCalculator 回填)。取数只走 InventoryQueryApi 只读契约
 *     (铁律 2/7);扫描护栏(页大小/行数上限)由调用方传入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowStockScanner {

    private final @Lazy InventoryQueryApi inventoryQueryApi;

    /** 扫描结果:低库存 SKU 聚合行(跨仓合并后)+ 实际扫描的库存行数 */
    public record ScanResult(List<ReplenishItem> items, int scanned) {
    }

    /**
     * 扫描低库存 SKU:可用 ≤ 阈值命中,跨仓合并(LinkedHashMap 稳定顺序);
     * 护栏:scanMaxRows 钳制单轮总行数,防大表拖死
     */
    public ScanResult scan(int lowStockThreshold, int scanPageSize, int scanMaxRows) {
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
                // 跨仓合并:同 skuId 可用/在途求和
                mergedBySku.merge(row.skuId(),
                        ReplenishItem.builder()
                                .skuId(row.skuId())
                                .qtyAvailable(row.qtyAvailable())
                                .qtyTransit(row.qtyTransit() == null ? 0 : row.qtyTransit())
                                .suggestQty(0)
                                .summary("")
                                .calcJson("")
                                .build(),
                        (a, b) -> ReplenishItem.builder()
                                .skuId(a.skuId())
                                .qtyAvailable(a.qtyAvailable() + b.qtyAvailable())
                                .qtyTransit(a.qtyTransit() + b.qtyTransit())
                                .suggestQty(0)
                                .summary("")
                                .calcJson("")
                                .build());
            }
            scanned += rows.size();
            pageNo++;
            if (rows.size() < scanPageSize) {
                break;
            }
        }
        log.info("低库存扫描完成:扫描 {} 行,命中 SKU {} 个(阈值≤{})", scanned, mergedBySku.size(), lowStockThreshold);
        return new ScanResult(new ArrayList<>(mergedBySku.values()), scanned);
    }
}
