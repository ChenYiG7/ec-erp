package com.own.erp.ai.graph;

import cn.hutool.core.collection.CollUtil;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.PurchaseQueryApi;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 聚合节点(#17 SAA Graph 采购建议工作流):SKU 级补货缺口按"最新采购供应商"
 *     分组聚合——映射走 PurchaseQueryApi.findLatestSupplierBySkuIds 只读契约(铁律 2/7,
 *     窗口函数取每 SKU 最近一笔非 DRAFT 采购行);行预估金额 = 最新单价×建议量(单价缺失按 0,
 *     禁猜价);无采购历史的 SKU 不纳入建议(无法定位供应商,建议无行动价值)计数上报;
 *     去重(2026-09-07 拍板语义同款):同供应商存在待确认(status=0)PURCHASE 建议即跳过该组,
 *     旧建议被采纳/忽略后若缺口仍在允许再产出;风险分级收口 persist(组内含缺货 SKU → HIGH)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseAggregateNode implements NodeAction {

    private final @Lazy PurchaseQueryApi purchaseQueryApi;
    private final AiSuggestionService aiSuggestionService;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<ReplenishItem> items = (List<ReplenishItem>) state.value(PurchaseStateKeys.KEY_ITEMS)
                .orElse(List.of());
        if (CollUtil.isEmpty(items)) {
            return Map.of(PurchaseStateKeys.KEY_GROUPS, List.of(), PurchaseStateKeys.KEY_NO_SUPPLIER, 0);
        }
        Map<Long, PurchaseQueryApi.SkuSupplierView> supplierBySku = new HashMap<>();
        for (PurchaseQueryApi.SkuSupplierView view : purchaseQueryApi
                .findLatestSupplierBySkuIds(items.stream().map(ReplenishItem::skuId).toList())) {
            if (view != null && view.skuId() != null && view.supplierId() != null) {
                supplierBySku.put(view.skuId(), view);
            }
        }
        List<PurchaseGroup> groups = aggregateBySupplier(items, supplierBySku);
        int noSupplier = countUnmapped(items, supplierBySku);
        int deduped = dedupPending(groups);
        log.info("采购聚合完成:SKU {} 个,供应商组 {} 个,无采购历史剔除 {} 个,待确认去重跳过 {} 组",
                items.size(), groups.size() + deduped, noSupplier, deduped);
        return Map.of(PurchaseStateKeys.KEY_GROUPS, groups,
                PurchaseStateKeys.KEY_NO_SUPPLIER, noSupplier);
    }

    /** 按供应商分组聚合(LinkedHashMap 稳定顺序;金额 BigDecimal 精确累加,禁 double) */
    private List<PurchaseGroup> aggregateBySupplier(List<ReplenishItem> items,
                                                    Map<Long, PurchaseQueryApi.SkuSupplierView> supplierBySku) {
        Map<Long, List<PurchaseGroup.Line>> linesBySupplier = new LinkedHashMap<>();
        Map<Long, String> nameBySupplier = new HashMap<>();
        for (ReplenishItem item : items) {
            PurchaseQueryApi.SkuSupplierView view = supplierBySku.get(item.skuId());
            if (view == null) {
                continue;
            }
            BigDecimal price = view.lastPrice() == null ? BigDecimal.ZERO : view.lastPrice();
            BigDecimal estAmount = price.multiply(BigDecimal.valueOf(item.suggestQty()));
            linesBySupplier.computeIfAbsent(view.supplierId(), k -> new ArrayList<>())
                    .add(PurchaseGroup.Line.builder()
                            .skuId(item.skuId())
                            .suggestQty(item.suggestQty())
                            .lastPrice(view.lastPrice())
                            .estAmount(estAmount)
                            .qtyAvailable(item.qtyAvailable())
                            .build());
            nameBySupplier.putIfAbsent(view.supplierId(), view.supplierName());
        }
        List<PurchaseGroup> groups = new ArrayList<>(linesBySupplier.size());
        for (Map.Entry<Long, List<PurchaseGroup.Line>> entry : linesBySupplier.entrySet()) {
            List<PurchaseGroup.Line> lines = entry.getValue();
            int totalQty = lines.stream().mapToInt(PurchaseGroup.Line::suggestQty).sum();
            BigDecimal estTotal = lines.stream().map(PurchaseGroup.Line::estAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean hasStockout = lines.stream().anyMatch(line -> line.qtyAvailable() <= 0);
            groups.add(PurchaseGroup.builder()
                    .supplierId(entry.getKey())
                    .supplierName(nameBySupplier.get(entry.getKey()))
                    .totalQty(totalQty)
                    .estAmount(estTotal)
                    .hasStockout(hasStockout)
                    .lines(lines)
                    .summary("")
                    .build());
        }
        return groups;
    }

    /** 无供应商映射的 SKU 计数(无采购历史,不产建议) */
    private int countUnmapped(List<ReplenishItem> items,
                              Map<Long, PurchaseQueryApi.SkuSupplierView> supplierBySku) {
        return (int) items.stream().filter(item -> !supplierBySku.containsKey(item.skuId())).count();
    }

    /** 同供应商已存在待确认建议则整组移除(定时/手动统一口径),返回移除组数;无组不查库 */
    private int dedupPending(List<PurchaseGroup> groups) {
        if (groups.isEmpty()) {
            return 0;
        }
        Set<Long> pending = aiSuggestionService.findPendingRefIds(
                AiConsts.TYPE_PURCHASE, AiConsts.REF_TYPE_SUPPLIER);
        int before = groups.size();
        groups.removeIf(group -> pending.contains(group.supplierId()));
        return before - groups.size();
    }
}
