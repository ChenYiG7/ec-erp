package com.own.erp.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.contract.SalesQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 补货量计算器 V2(#6 补货算法升级,(s,S) 策略 + 安全库存;#17 采购建议工作流复用,
 *     公式单一来源两工作流组合复用;纯程序规则算建议量不调模型,零 token——铁律 8):
 *     相比 V1.5 均值公式(建议量=覆盖天数×日均−可用−在途)的两点本质升级:
 *     ①显式建模采购提前期(lead-time-days)——补货点 = 提前期需求 + 安全库存,库存位置低于补货点才触发,
 *     均值公式只看"覆盖天数够不够",无法表达"提前期内会不会断货";
 *     ②需求波动进入公式(服务水平 service-level)——安全库存 = z×σ×√LT,σ = 窗口逐日销量样本标准差
 *     (零销日补 0,SalesQueryApi.listDailyQtyBySku 逐日序列),z 按档位 {0.90,0.95,0.98,0.99} 最近邻映射;
 *     低均值高波动(闪购/漏单爆款)场景 V1.5 会漏,V2 由安全垫捕捉。
 *     公式组:μ=窗口销量合计/窗口天数;SS=ceil(z·σ·√LT);ROP=ceil(μ·LT)+SS(触发线);
 *     S=ceil(μ·(LT+覆盖天数))+SS(补足线,覆盖天数语义不变:提前期之外额外覆盖);
 *     触发=库存位置(可用+在途)≤ROP,建议量=S−库存位置(≤0 剔除,<最小建议量提到下限);
 *     零动销 SKU(μ=0 ⇒ σ=0 ⇒ ROP=0)不触发不硬补,V1.5 拍板语义保持。
 *     算法明细(μ/σ/SS/ROP/S)回填 calcJson,persist 透传 payloadJson 供人工判读。
 *     参数由调用方传入(#18 系统设置口径:AiRuntimeProperties 每轮取值,组件无状态可复用)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplenishCalculator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 服务水平 → z 系数档位(单侧分位数;运营侧只配服务水平,不暴露 z-score 概念) */
    private static final Map<BigDecimal, Double> SERVICE_LEVEL_Z = Map.of(
            new BigDecimal("0.90"), 1.2816,
            new BigDecimal("0.95"), 1.6449,
            new BigDecimal("0.98"), 2.0537,
            new BigDecimal("0.99"), 2.3263);

    private final @Lazy SalesQueryApi salesQueryApi;

    /** 建议量计算:回填 suggestQty 与 calcJson,剔除不需要补货的 SKU(返回新列表,不改入参) */
    public List<ReplenishItem> calculate(List<ReplenishItem> items, int salesWindowDays,
                                         int coverageDays, int minSuggestQty,
                                         int leadTimeDays, BigDecimal serviceLevel) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<LocalDate, Integer>> seriesBySku = salesQueryApi.listDailyQtyBySku(
                items.stream().map(ReplenishItem::skuId).collect(Collectors.toSet()),
                salesWindowDays);
        double z = zOf(serviceLevel);
        double sqrtLeadTime = Math.sqrt(Math.max(leadTimeDays, 0));
        List<ReplenishItem> calculated = new ArrayList<>(items.size());
        int skipped = 0;
        for (ReplenishItem item : items) {
            DailyStats stats = statsOf(seriesBySku.get(item.skuId()), salesWindowDays);
            int safetyStock = (int) Math.min(Integer.MAX_VALUE,
                    Math.ceil(z * stats.sigma() * sqrtLeadTime));
            int reorderPoint = (int) Math.min(Integer.MAX_VALUE,
                    (long) Math.ceil(stats.avgDaily() * leadTimeDays) + safetyStock);
            int targetQty = (int) Math.min(Integer.MAX_VALUE,
                    (long) Math.ceil(stats.avgDaily() * (leadTimeDays + coverageDays)) + safetyStock);
            int inventoryPosition = item.qtyAvailable() + item.qtyTransit();
            // 零动销(死 SKU)不硬补;库存位置高于补货点 = 提前期内不断货,不建议
            if (stats.avgDaily() <= 0 || inventoryPosition > reorderPoint) {
                skipped++;
                continue;
            }
            int need = targetQty - inventoryPosition;
            if (need <= 0) {
                skipped++;
                continue;
            }
            int suggestQty = (int) Math.min(Integer.MAX_VALUE, Math.max(minSuggestQty, need));
            calculated.add(item.toBuilder()
                    .suggestQty(suggestQty)
                    .calcJson(toCalcJson(item, suggestQty, stats, salesWindowDays, leadTimeDays,
                            serviceLevel, safetyStock, reorderPoint, targetQty))
                    .build());
        }
        log.info("补货量计算完成(V2 (s,S) 策略):候选 {} 个,建议 {} 个,不需要补货剔除 {} 个",
                items.size(), calculated.size(), skipped);
        return calculated;
    }

    /** 服务水平 → z 系数:按档位最近邻映射;null/非法值按 0.95 档(与配置默认一致) */
    static double zOf(BigDecimal serviceLevel) {
        if (serviceLevel == null) {
            return SERVICE_LEVEL_Z.get(new BigDecimal("0.95"));
        }
        BigDecimal nearest = null;
        double nearestGap = Double.MAX_VALUE;
        for (BigDecimal level : SERVICE_LEVEL_Z.keySet()) {
            double gap = Math.abs(level.doubleValue() - serviceLevel.doubleValue());
            if (gap < nearestGap) {
                nearestGap = gap;
                nearest = level;
            }
        }
        return SERVICE_LEVEL_Z.get(nearest);
    }

    /** 逐日序列 → 统计量:μ=Σ/N(缺日按 0 补齐窗口天数),σ=样本标准差(n−1;N=1 退化 0) */
    private DailyStats statsOf(Map<LocalDate, Integer> series, int windowDays) {
        if (series == null || series.isEmpty()) {
            return new DailyStats(0, 0);
        }
        long sum = 0;
        long sumSq = 0;
        for (Integer qty : series.values()) {
            if (qty == null) {
                continue;
            }
            sum += qty;
            sumSq += (long) qty * qty;
        }
        double n = windowDays;
        double avgDaily = n == 0 ? 0 : sum / n;
        // 全 N 点样本方差(零销日贡献 0):var = (Σx² − n·μ²)/(n−1),浮点尾差钳 0
        double variance = n <= 1 ? 0 : (sumSq - n * avgDaily * avgDaily) / (n - 1);
        double sigma = variance <= 0 ? 0 : Math.sqrt(variance);
        log.debug("SKU 需求统计:窗口 {} 天,μ={},σ={}", windowDays, avgDaily, sigma);
        return new DailyStats(avgDaily, sigma);
    }

    /** 算法明细 JSON(persist 透传 payloadJson;含旧三字段键,消费侧向后兼容) */
    private String toCalcJson(ReplenishItem item, int suggestQty, DailyStats stats, int windowDays,
                              int leadTimeDays, BigDecimal serviceLevel,
                              int safetyStock, int reorderPoint, int targetQty) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("algorithm", "REORDER_POINT_V2");
        payload.put("salesWindowDays", windowDays);
        payload.put("leadTimeDays", leadTimeDays);
        payload.put("serviceLevel", serviceLevel == null ? "0.95" : serviceLevel.toPlainString());
        payload.put("avgDaily", round(stats.avgDaily()));
        payload.put("sigma", round(stats.sigma()));
        payload.put("safetyStock", safetyStock);
        payload.put("reorderPoint", reorderPoint);
        payload.put("targetQty", targetQty);
        payload.put("qtyAvailable", item.qtyAvailable());
        payload.put("qtyTransit", item.qtyTransit());
        payload.put("suggestQty", suggestQty);
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            // LinkedHashMap 纯数值序列化不会失败,兜底防御(空串回落 persist 旧形态)
            log.warn("补货 V2 算法明细序列化失败,payload 回落旧三字段形态:{}", e.getMessage());
            return "";
        }
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /** 单 SKU 需求统计量(μ 日均 / σ 样本标准差) */
    private record DailyStats(double avgDaily, double sigma) {
    }
}
