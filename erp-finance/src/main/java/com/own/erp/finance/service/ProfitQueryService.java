package com.own.erp.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitRow;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.contract.ProfitDailyTrendRow;
import com.own.erp.contract.ProfitSkuRankRow;
import com.own.erp.contract.QueryPage;
import com.own.erp.finance.entity.PlatformFeeRate;
import com.own.erp.finance.mapper.FirstLegQueryMapper;
import com.own.erp.finance.mapper.ProfitQueryMapper;
import com.own.erp.finance.profit.OrderProfitAmountGroup;
import com.own.erp.finance.profit.OrderProfitLine;
import com.own.erp.finance.response.FirstLegSkuAllocSumRow;
import com.own.erp.platform.unified.UnifiedSettlement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.TreeMap;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 实时销售利润组装(#19③ 三口径第一层,docs/02 §14/03 §6.1 口径拍板):
 *     订单行粒度 利润 = 售价(CNY) − 出库成本(CNY) − 平台佣金(CNY),不落库实时算(小时级新鲜度天然满足)。
 *     组装三路数据:①主查询(订单行 join 订单,已支付态三态)分页/全量(XML ProfitQueryMapper);
 *     ②出库成本批量聚合(OUT_SHIP 移动加权快照,未出库=NULL 不猜);
 *     ③平台佣金批量归集(settlement_detail COMMISSION 按店铺+平台订单行);无实际佣金时按
 *     platform_fee_rate 费率估算(2026-09-11 #19 预估模型:实际优先,估算 −售价CNY×费率,
 *     行级 commissionEstimated 标志,无费率仍 NULL 不猜;FBA 仓储类无费率不估);
 *     ④汇率逐行按下单日回溯 resolveRate 唯一口径(CNY 短路;缺报价折 NULL 禁猜)。
 *     缺口不静默归零:missing 标志进行模型/汇总单独计数(费用事实纪律同 #19② 勾稽);
 *     profitCny:缺成本/缺汇率→NULL,佣金缺实际且无预估→售价−成本(毛利,标志位区分),有佣金(实际/预估)→扣佣。
 *     全量汇总 SQL 硬 LIMIT 20000 防御,量级增长落库方案随 V2 周期口径
 */
@Service
@RequiredArgsConstructor
public class ProfitQueryService {

    /** 佣金归集复合键分隔符(shopId 与平台行号拼接,店铺内平台行号归集口径同 #19② DDL) */
    private static final String COMMISSION_KEY_DELIMITER = "|";

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ProfitQueryMapper profitQueryMapper;
    private final ExchangeRateService exchangeRateService;
    /** #19 预估费用模型:佣金缺口按费率表估算(实际佣金优先,无费率不猜) */
    private final PlatformFeeRateService platformFeeRateService;
    /** #33 利润第三层:头程运费分摊批量合计(同模块 first_leg_alloc 数据面) */
    private final FirstLegQueryMapper firstLegQueryMapper;

    /** 订单行利润分页(下单时间倒序)。
     *  数据权限(#27①):shopIds 由 ProfitQueryApiImpl 强制装配,null=不过滤;空列表=不可见任何店铺(短路零结果) */
    public QueryPage<OrderProfitRow> page(OrderProfitQuery query) {
        if (shopScopeEmpty(query)) {
            return QueryPage.of(List.of(), 0);
        }
        Page<OrderProfitLine> page = profitQueryMapper.selectProfitLines(
                new Page<>(query.pageNoOrDefault(), query.pageSizeOrDefault()),
                query.shopId(), query.platform(), query.skuId(), query.dateFrom(), query.dateTo(), query.shopIds());
        return QueryPage.of(assemble(page.getRecords()), page.getTotal());
    }

    /** 同条件汇总(全量行聚合,与行口径一致;缺口单独计数不静默归零);空授权集短路 = 全零汇总 */
    public OrderProfitSummary summarize(OrderProfitQuery query) {
        List<OrderProfitRow> rows = shopScopeEmpty(query) ? List.of()
                : assemble(profitQueryMapper.selectProfitLinesAll(
                        query.shopId(), query.platform(), query.skuId(), query.dateFrom(), query.dateTo(), query.shopIds()));
        BigDecimal sales = sumOf(rows, OrderProfitRow::salesCny);
        BigDecimal profit = sumOf(rows, OrderProfitRow::profitCny);
        return new OrderProfitSummary(
                rows.size(),
                sales,
                sumOf(rows, OrderProfitRow::costCny),
                sumOf(rows, OrderProfitRow::commissionCny),
                profit,
                rows.stream().filter(row -> row.rate() == null).count(),
                rows.stream().filter(OrderProfitRow::costMissing).count(),
                rows.stream().filter(OrderProfitRow::commissionMissing).count(),
                grossMargin(sales, profit));
    }

    /** 利润日趋势(#21):全量行按下单日聚合,口径与 summarize 同源(同一装配管线,非独立 SQL);日期升序 */
    public List<ProfitDailyTrendRow> listDailyTrend(OrderProfitQuery query) {
        List<OrderProfitRow> rows = shopScopeEmpty(query) ? List.of()
                : assemble(profitQueryMapper.selectProfitLinesAll(
                        query.shopId(), query.platform(), query.skuId(), query.dateFrom(), query.dateTo(), query.shopIds()));
        Map<LocalDate, List<OrderProfitRow>> byDate = new TreeMap<>();
        for (OrderProfitRow row : rows) {
            byDate.computeIfAbsent(row.orderTime().toLocalDate(), k -> new ArrayList<>()).add(row);
        }
        List<ProfitDailyTrendRow> trend = new ArrayList<>(byDate.size());
        for (Map.Entry<LocalDate, List<OrderProfitRow>> entry : byDate.entrySet()) {
            List<OrderProfitRow> dayRows = entry.getValue();
            BigDecimal sales = sumOf(dayRows, OrderProfitRow::salesCny);
            BigDecimal profit = sumOf(dayRows, OrderProfitRow::profitCny);
            trend.add(new ProfitDailyTrendRow(entry.getKey(), dayRows.size(),
                    sales,
                    sumOf(dayRows, OrderProfitRow::costCny),
                    sumOf(dayRows, OrderProfitRow::commissionCny),
                    profit,
                    grossMargin(sales, profit)));
        }
        return trend;
    }

    /** SKU 利润排行(#21):按内部 SKU 聚合(仅已绑定行,未绑定行无 SKU 维度不参与),利润降序,topN 钳制 1..100;
     *  #33 第三层 enrichment:统计窗内头程分摊按方案 B 整窗摊入(shipped_at 锚点,复用查询窗参数) */
    public List<ProfitSkuRankRow> listSkuProfitRank(OrderProfitQuery query, int topN) {
        int limit = Math.max(1, Math.min(topN, 100));
        List<OrderProfitRow> rows = shopScopeEmpty(query) ? List.of()
                : assemble(profitQueryMapper.selectProfitLinesAll(
                        query.shopId(), query.platform(), query.skuId(), query.dateFrom(), query.dateTo(), query.shopIds()));
        Map<Long, List<OrderProfitRow>> bySku = new LinkedHashMap<>();
        for (OrderProfitRow row : rows) {
            if (row.skuId() != null) {
                bySku.computeIfAbsent(row.skuId(), k -> new ArrayList<>()).add(row);
            }
        }
        Map<Long, BigDecimal> firstLegBySku = sumFirstLegBySku(bySku.keySet(), query.dateFrom(), query.dateTo());
        List<ProfitSkuRankRow> rank = new ArrayList<>(bySku.size());
        for (Map.Entry<Long, List<OrderProfitRow>> entry : bySku.entrySet()) {
            List<OrderProfitRow> skuRows = entry.getValue();
            BigDecimal sales = sumOf(skuRows, OrderProfitRow::salesCny);
            BigDecimal profit = sumOf(skuRows, OrderProfitRow::profitCny);
            BigDecimal firstLeg = firstLegBySku.getOrDefault(entry.getKey(), ZERO);
            rank.add(new ProfitSkuRankRow(entry.getKey(), firstNonNull(skuRows), skuRows.size(),
                    skuRows.stream().mapToLong(row -> row.quantity() == null ? 0L : row.quantity()).sum(),
                    sales,
                    sumOf(skuRows, OrderProfitRow::costCny),
                    sumOf(skuRows, OrderProfitRow::commissionCny),
                    profit,
                    grossMargin(sales, profit),
                    firstLeg,
                    profit.subtract(firstLeg)));
        }
        rank.sort(Comparator.comparing(ProfitSkuRankRow::profitCny).reversed());
        return rank.size() > limit ? rank.subList(0, limit) : rank;
    }

    /**
     * 头程分摊批量合计(#33 方案 B):按排名 SKU 集合批量取(空集短路不触库),
     * 时间窗复用查询参数(下单窗=统计窗,shipped_at 锚点);无分摊 SKU 缺键调用方按 0 兜底
     */
    private Map<Long, BigDecimal> sumFirstLegBySku(Collection<Long> skuIds, LocalDateTime from, LocalDateTime to) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> sums = new HashMap<>();
        for (FirstLegSkuAllocSumRow row : firstLegQueryMapper.sumAllocBySkuIds(skuIds, from, to)) {
            sums.put(row.getSkuId(), row.getAllocAmountCny() == null ? ZERO : row.getAllocAmountCny());
        }
        return sums;
    }

    /** 商品名称快照:同 SKU 多订单行取首见非空(禁 null 出契约) */
    private String firstNonNull(List<OrderProfitRow> skuRows) {
        return skuRows.stream().map(OrderProfitRow::productName).filter(Objects::nonNull).findFirst().orElse("");
    }

    /** 组装:主查询行 → 批量补成本/佣金/费率索引 → 逐行汇率回溯折算(分页 ≤200 行逐行 LIMIT 1 查询可接受) */
    private List<OrderProfitRow> assemble(List<OrderProfitLine> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> costByItem = loadCosts(lines);
        Map<String, BigDecimal> commissionByKey = loadCommissions(lines);
        // 佣金费率一次性全量载入(费率表量级小)按平台分组,内存回溯挑选,禁逐行查库 N+1
        Map<String, List<PlatformFeeRate>> commissionRateIndex = loadCommissionRateIndex();
        List<OrderProfitRow> rows = new ArrayList<>(lines.size());
        for (OrderProfitLine line : lines) {
            rows.add(toRow(line, costByItem, commissionByKey, commissionRateIndex));
        }
        return rows;
    }

    /** 预估佣金费率索引:全站点 COMMISSION 费率按平台分组(费率表量级小;空表返回空 Map) */
    private Map<String, List<PlatformFeeRate>> loadCommissionRateIndex() {
        List<PlatformFeeRate> rates = platformFeeRateService.listGlobalRates(
                UnifiedSettlement.FeeType.COMMISSION.name());
        Map<String, List<PlatformFeeRate>> index = new HashMap<>();
        for (PlatformFeeRate rate : rates) {
            index.computeIfAbsent(rate.getPlatform(), k -> new ArrayList<>()).add(rate);
        }
        return index;
    }

    /** 出库成本:按内部订单行归集(未命中的行=未出库,NULL 语义) */
    private Map<Long, BigDecimal> loadCosts(List<OrderProfitLine> lines) {
        List<Long> orderItemIds = lines.stream().map(OrderProfitLine::orderItemId).distinct().toList();
        Map<Long, BigDecimal> costByItem = new HashMap<>(orderItemIds.size());
        for (OrderProfitAmountGroup group : profitQueryMapper.sumCostByOrderItemIds(orderItemIds)) {
            costByItem.put(group.orderItemId(), group.amount());
        }
        return costByItem;
    }

    /** 平台佣金:按 店铺+平台订单行 归集(行号缺失的行直接无佣金,待结算语义) */
    private Map<String, BigDecimal> loadCommissions(List<OrderProfitLine> lines) {
        List<String> platformItemIds = lines.stream()
                .map(OrderProfitLine::platformOrderItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (platformItemIds.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> commissionByKey = new HashMap<>(platformItemIds.size());
        for (OrderProfitAmountGroup group : profitQueryMapper.sumCommissionByPlatformItemIds(platformItemIds)) {
            commissionByKey.put(group.shopId() + COMMISSION_KEY_DELIMITER + group.platformOrderItemId(),
                    group.amount());
        }
        return commissionByKey;
    }

    private OrderProfitRow toRow(OrderProfitLine line, Map<Long, BigDecimal> costByItem,
                                 Map<String, BigDecimal> commissionByKey,
                                 Map<String, List<PlatformFeeRate>> commissionRateIndex) {
        BigDecimal rate = exchangeRateService.resolveRate(line.currency(), line.orderTime());
        BigDecimal salesCny = rate == null || line.itemAmount() == null ? null
                : line.itemAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costCny = costByItem.get(line.orderItemId());
        // 实际佣金优先:结算报告 COMMISSION 归集(V1 原值口径,报告原币未折 CNY——已知边界:
        // 跨境行级折算待真实结算数据校准后立项,拍板范围见 TODO#32 devlog 补篇遗留节;预估佣金已按 CNY 估算)
        BigDecimal commissionCny = line.platformOrderItemId() == null ? null
                : commissionByKey.get(line.shopId() + COMMISSION_KEY_DELIMITER + line.platformOrderItemId());
        boolean costMissing = costCny == null;
        boolean commissionMissing = commissionCny == null;
        // 无实际佣金且售价可折 CNY 时,按平台费率表估佣金(−售价×费率,报告符号为负);无费率不猜保持 NULL
        boolean commissionEstimated = false;
        if (commissionMissing && salesCny != null) {
            PlatformFeeRate feeRate = PlatformFeeRateService.pickFeeRate(
                    commissionRateIndex.get(line.platform()), line.orderTime().toLocalDate());
            if (feeRate != null) {
                commissionCny = salesCny.multiply(feeRate.getRate()).negate().setScale(2, RoundingMode.HALF_UP);
                commissionEstimated = true;
            }
        }
        // 利润=售价−成本+佣金(佣金带符号为负,加负即扣减;实际/预估都缺退化为毛利,标志位区分);
        // 缺成本或缺汇率→NULL(禁把缺口静默算成满利润)
        BigDecimal profitCny = salesCny == null || costMissing ? null
                : salesCny.subtract(costCny).add(commissionCny == null ? ZERO : commissionCny);
        return new OrderProfitRow(line.orderItemId(), line.orderId(), line.platformOrderId(), line.platform(),
                line.orderTime(), line.shopId(), line.platformOrderItemId(), line.platformSku(), line.productName(),
                line.skuId(), line.quantity(), line.itemAmount(), line.currency(), rate, salesCny, costCny,
                commissionCny, profitCny, costMissing, commissionMissing, commissionEstimated);
    }

    private BigDecimal sumOf(List<OrderProfitRow> rows, java.util.function.Function<OrderProfitRow, BigDecimal> getter) {
        return rows.stream().map(getter).filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
    }

    /** 毛利率(%,profit/sales×100 保留 1 位小数 HALF_UP);sales 空/≤0 返 NULL 禁猜(#21 拍板后端统一下发) */
    private BigDecimal grossMargin(BigDecimal sales, BigDecimal profit) {
        return sales == null || sales.signum() <= 0 ? null
                : profit.multiply(BigDecimal.valueOf(100)).divide(sales, 1, RoundingMode.HALF_UP);
    }

    /** 数据权限空授权集判定(#27①):shopIds 非 null 且空 = 不可见任何店铺,四方法统一短路零结果 */
    private boolean shopScopeEmpty(OrderProfitQuery query) {
        return query.shopIds() != null && query.shopIds().isEmpty();
    }
}
