package com.own.erp.ai.graph;

import cn.hutool.core.collection.CollUtil;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.service.AiSuggestionService;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.ProfitQueryApi;
import com.own.erp.contract.ProfitSkuRankRow;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.SalesQueryApi;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 取数节点(#17 落位表「智能选品」三期提前落地,拍板 2026-09-08):
 *     候选域 = 启用商品 ∩ 有库存行(inventory 行 change() 自动建行且不删,已动销 SKU 天然在列);
 *     完全无信号行(30 天零动销且可用 ≤0)剔除——死 SKU 无选品价值;零动销但有库存保留
 *     (滞销候选,综合分天然垫底,NO_SALES 标记供人工判读)。
 *     数据面全走既有只读契约(铁律 2/7,零新契约零 DDL):库存跨仓合并(InventoryQueryApi)/
 *     销量规模与逐日序列(SalesQueryApi 30d)/毛利(ProfitQueryApi.listSkuProfitRank 30d 窗口)。
 *     逐源隔离拍板:库存或商品扫描失败本轮中止或收缩(无候选无从评起,已扫部分生效);
 *     销量/利润批量查询失败降级为缺数据标记继续评分(评分口径仍确定,维度记中性不猜值)。
 *     去重:同 SKU 存在待确认 SELECTION 建议即跳过(采纳/忽略后可再产出,确认闭环自然运转)。
 *     时间统一走注入 Clock(docs/07 §10);窗口常量(评分窗 30d/趋势半窗 7d)属实现细节不入配置
 *     (同 CopyCollectNode.SKU_PROMPT_MAX 拍板口径)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectionCollectNode implements NodeAction {

    /** 评分窗口(天,含今日):销量规模/趋势/毛利统一窗口 */
    static final int SCORE_WINDOW_DAYS = 30;

    /** 趋势半窗(天):近 7 天 vs 前 7 天日均对比 */
    static final int TREND_HALF_WINDOW_DAYS = 7;

    /** 利润排行拉取上限(契约钳制 ≤100;SKU 数量级小 V1 够用,扩容随 ProfitQueryApi 契约) */
    static final int PROFIT_RANK_TOP_N = 100;

    private final @Lazy InventoryQueryApi inventoryQueryApi;
    private final @Lazy GoodsQueryApi goodsQueryApi;
    private final @Lazy SalesQueryApi salesQueryApi;
    private final @Lazy ProfitQueryApi profitQueryApi;
    private final ErpAiProperties props;
    private final AiSuggestionService aiSuggestionService;
    private final Clock pullClock;

    /** 库存扫描结果:skuId → [可用, 在途] 跨仓合并 + 实际扫描行数 */
    record StockScan(Map<Long, int[]> stockBySku, int scanned) {
    }

    /** 启用商品 SKU 信息(skuCode + 所属 SPU 名称快照) */
    record SkuInfo(String skuCode, String productName) {
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        LocalDateTime now = LocalDateTime.now(pullClock);
        // 1. 库存全量扫描,跨仓合并(库存面为空本轮零候选)
        StockScan stock = scanInventory();
        if (stock.stockBySku().isEmpty()) {
            log.info("选品取数完成:库存面为空,本轮零候选");
            return Map.of(SelectionStateKeys.KEY_CANDIDATES, List.of(),
                    SelectionStateKeys.KEY_SCANNED, 0);
        }
        // 2. 启用商品 SKU 信息映射(停用/缺失商品的库存行不参与选品)
        Map<Long, SkuInfo> skuInfoById = scanEnabledSkuInfos();
        // 3. 销量面批量取数(失败降级空 map,候选走缺数据标记)
        SalesSeries sales = fetchSales(stock.stockBySku().keySet());
        // 4. 毛利面批量取数(30d 窗口利润排行,失败降级空 map)
        Map<Long, ProfitSkuRankRow> rankBySku = fetchProfitRank(now);
        // 5. 候选行装配
        List<SelectionCandidate> candidates = assembleCandidates(
                stock.stockBySku(), skuInfoById, sales, rankBySku);
        // 6. 去重:同 SKU 存在待确认建议即跳过
        int skipped = dedupPending(candidates);
        log.info("选品取数完成:扫描库存 {} 行,候选 SKU {} 个,去重跳过 {} 个",
                stock.scanned(), candidates.size(), skipped);
        return Map.of(SelectionStateKeys.KEY_CANDIDATES, candidates,
                SelectionStateKeys.KEY_SCANNED, stock.scanned());
    }

    /**
     * 分页扫 inventory 全量,跨仓合并(可用/在途求和,LinkedHashMap 稳定顺序);
     * 护栏 scanPageSize/scanMaxRows 走 erp.ai.selection.*(yml);失败只记日志返回已扫部分
     */
    private StockScan scanInventory() {
        Map<Long, int[]> mergedBySku = new LinkedHashMap<>();
        int scanned = 0;
        int pageNo = 1;
        int scanPageSize = props.getSelection().getScanPageSize();
        int scanMaxRows = props.getSelection().getScanMaxRows();
        try {
            while (scanned < scanMaxRows) {
                QueryPage<InventoryQueryApi.InventoryView> page = inventoryQueryApi.pageInventory(
                        InventoryQueryApi.InventoryFilter.builder()
                                .pageNo(pageNo).pageSize(scanPageSize).build());
                List<InventoryQueryApi.InventoryView> rows = page == null ? null : page.list();
                if (CollUtil.isEmpty(rows)) {
                    break;
                }
                for (InventoryQueryApi.InventoryView row : rows) {
                    if (row.skuId() == null) {
                        continue;
                    }
                    mergedBySku.merge(row.skuId(),
                            new int[]{nvl(row.qtyAvailable()), nvl(row.qtyTransit())},
                            (a, b) -> new int[]{a[0] + b[0], a[1] + b[1]});
                }
                scanned += rows.size();
                pageNo++;
                if (rows.size() < scanPageSize) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("选品库存扫描执行失败,本轮返回已扫部分 :{}", e.getMessage(), e);
        }
        return new StockScan(mergedBySku, scanned);
    }

    /**
     * 分页扫启用商品 → 逐商品拉 SKU 行建 skuId→SkuInfo 映射;
     * 逐商品隔离(单商品 SKU 查询失败只跳过该商品),商品扫描失败返回已扫部分
     */
    private Map<Long, SkuInfo> scanEnabledSkuInfos() {
        Map<Long, SkuInfo> skuInfoById = new HashMap<>();
        int scanPageSize = props.getSelection().getScanPageSize();
        int scanMaxRows = props.getSelection().getScanMaxRows();
        int scanned = 0;
        int pageNo = 1;
        try {
            while (scanned < scanMaxRows) {
                QueryPage<GoodsQueryApi.ProductView> page = goodsQueryApi.pageProducts(
                        GoodsQueryApi.ProductFilter.builder().status(1)
                                .pageNo(pageNo).pageSize(scanPageSize).build());
                List<GoodsQueryApi.ProductView> rows = page == null ? null : page.list();
                if (CollUtil.isEmpty(rows)) {
                    break;
                }
                for (GoodsQueryApi.ProductView product : rows) {
                    if (product == null || product.id() == null) {
                        continue;
                    }
                    try {
                        for (GoodsQueryApi.SkuView sku : goodsQueryApi.listSkusByProductId(product.id())) {
                            if (sku != null && sku.id() != null) {
                                skuInfoById.put(sku.id(),
                                        new SkuInfo(sku.skuCode(), product.name()));
                            }
                        }
                    } catch (Exception e) {
                        log.warn("商品[{}]SKU 行装配失败,本轮跳过 :{}", product.id(), e.getMessage());
                    }
                }
                scanned += rows.size();
                pageNo++;
                if (rows.size() < scanPageSize) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("选品商品扫描执行失败,已扫部分生效 :{}", e.getMessage(), e);
        }
        return skuInfoById;
    }

    /** 销量批量取数:30 天合计 + 逐日序列;失败返回空结构(候选全部走零销量/缺数据降级) */
    private record SalesSeries(Map<Long, Integer> qty30BySku,
                               Map<Long, Map<LocalDate, Integer>> dailyBySku) {

        static final SalesSeries EMPTY = new SalesSeries(Map.of(), Map.of());

        int qty30(Long skuId) {
            return qty30BySku.getOrDefault(skuId, 0);
        }

        int halfWindowSum(Long skuId, int offsetDays, int halfWindowDays, LocalDate today) {
            Map<LocalDate, Integer> daily = dailyBySku.get(skuId);
            if (CollUtil.isEmpty(daily)) {
                return 0;
            }
            int sum = 0;
            for (int i = offsetDays; i < offsetDays + halfWindowDays; i++) {
                sum += daily.getOrDefault(today.minusDays(i), 0);
            }
            return sum;
        }
    }

    private SalesSeries fetchSales(Set<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return SalesSeries.EMPTY;
        }
        try {
            return new SalesSeries(
                    salesQueryApi.sumQtyBySku(skuIds, SCORE_WINDOW_DAYS),
                    salesQueryApi.listDailyQtyBySku(skuIds, SCORE_WINDOW_DAYS));
        } catch (Exception e) {
            log.warn("选品销量批量查询失败,全部候选走零销量降级 :{}", e.getMessage(), e);
            return SalesSeries.EMPTY;
        }
    }

    /** 毛利面:利润排行(30d 窗口)按 skuId 对齐;失败返回空 map(候选打 MARGIN_MISSING) */
    private Map<Long, ProfitSkuRankRow> fetchProfitRank(LocalDateTime now) {
        try {
            return profitQueryApi.listSkuProfitRank(
                    new OrderProfitQuery(null, null, null,
                            now.minusDays(SCORE_WINDOW_DAYS), now, null, null),
                    PROFIT_RANK_TOP_N)
                    .stream()
                    .collect(Collectors.toMap(ProfitSkuRankRow::skuId, row -> row, (a, b) -> a));
        } catch (Exception e) {
            log.warn("选品利润排行查询失败,全部候选走缺毛利降级 :{}", e.getMessage(), e);
            return Map.of();
        }
    }

    /** 候选行装配:启用商品 ∩ 有库存行,剔除完全无信号行(零动销 + 零可用) */
    private List<SelectionCandidate> assembleCandidates(Map<Long, int[]> stockBySku,
                                                        Map<Long, SkuInfo> skuInfoById,
                                                        SalesSeries sales,
                                                        Map<Long, ProfitSkuRankRow> rankBySku) {
        LocalDate today = LocalDate.now(pullClock);
        List<SelectionCandidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, int[]> entry : stockBySku.entrySet()) {
            SkuInfo skuInfo = skuInfoById.get(entry.getKey());
            if (skuInfo == null) {
                continue;
            }
            int available = entry.getValue()[0];
            int transit = entry.getValue()[1];
            int qty30 = sales.qty30(entry.getKey());
            if (qty30 == 0 && available <= 0) {
                continue;
            }
            candidates.add(buildCandidate(entry.getKey(), skuInfo, available, transit,
                    qty30, sales.halfWindowSum(entry.getKey(), 0, TREND_HALF_WINDOW_DAYS, today),
                    sales.halfWindowSum(entry.getKey(), TREND_HALF_WINDOW_DAYS,
                            TREND_HALF_WINDOW_DAYS, today),
                    rankBySku.get(entry.getKey())));
        }
        return candidates;
    }

    /** 单候选装配:毛利缺口(排行未覆盖/无销售额)打 MARGIN_MISSING,禁除零禁猜值 */
    private SelectionCandidate buildCandidate(Long skuId, SkuInfo skuInfo, int available,
                                              int transit, int qty30, int recent7, int prior7,
                                              ProfitSkuRankRow rank) {
        SelectionCandidate.SelectionCandidateBuilder builder = SelectionCandidate.builder()
                .skuId(skuId)
                .skuCode(skuInfo.skuCode())
                .productName(skuInfo.productName())
                .qtyAvailable(available)
                .qtyTransit(transit)
                .qty30(qty30)
                .recent7(recent7)
                .prior7(prior7)
                .salesCny(null).profitCny(null).margin(null)
                .salesDim(null).trendDim(null).marginDim(null)
                .score(null).risk(AiConsts.RISK_LOW)
                .flags(new ArrayList<>())
                .summary("")
                .llmScored(false);
        if (rank != null && rank.salesCny() != null && rank.salesCny().signum() > 0) {
            BigDecimal profit = rank.profitCny() == null ? BigDecimal.ZERO : rank.profitCny();
            builder.salesCny(rank.salesCny())
                    .profitCny(profit)
                    .margin(profit.divide(rank.salesCny(), 4, RoundingMode.HALF_UP));
        } else {
            builder.flags(new ArrayList<>(List.of(SelectionCandidate.FLAG_MARGIN_MISSING)));
        }
        return builder.build();
    }

    /** 同 SKU 存在待确认 SELECTION 建议则移除,返回移除数;无候选不查库 */
    private int dedupPending(List<SelectionCandidate> candidates) {
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<Long> pending = aiSuggestionService.findPendingRefIds(
                AiConsts.TYPE_SELECTION, AiConsts.REF_TYPE_GOODS_SKU);
        int before = candidates.size();
        candidates.removeIf(candidate -> pending.contains(candidate.skuId()));
        return before - candidates.size();
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
