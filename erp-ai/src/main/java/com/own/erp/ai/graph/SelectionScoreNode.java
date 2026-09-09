package com.own.erp.ai.graph;

import cn.hutool.core.collection.CollUtil;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.constant.AiConsts;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 评分节点(#17 智能选品,纯程序零 token,拍板口径:选品评分必须确定性可复算——
 *     运营拿同一份数据手工代入公式应得同分,与异常检测"LLM 评风险"反向:LLM 只写推荐理由):
 *     三维加权(权重 sys_config 三键,按和归一化,Σ≤0 回落代码默认 0.4/0.3/0.3):
 *     ①销量规模 = 近30天销量 / 候选集最大销量 × 100(相对分,候选集内归一);
 *     ②动销趋势 = 近7天日均 vs 前7天日均:ratio=recent/prior,分=clamp(ratio×50,0,100)
 *       (持平=50,翻倍=100,腰斩=25;prior=0 且 recent>0 记 100 新起量,recent=0 记 0 停卖);
 *     ③毛利 = margin/30% × 100 钳 0..100(30% 毛利率=满分;缺失记中性 50 打 MARGIN_MISSING)。
 *     综合分 = Σ 权重×维度分(1 位小数 HALF_UP);风险规则定级:
 *     HIGH=在卖毛利为负(LOSS_MAKING)或近期有动销但断货(STOCKOUT);MID=毛利数据缺失或零动销;LOW=其余。
 *     入选 = 综合分降序取前 persistMaxItems(yml 护栏,默认 20;同分按 skuId 升序稳定排序),
 *     未入选行直接丢弃不落库(建议表防噪音)。
 *     窗口语义收口 SelectionCollectNode(30d 评分窗/7d 趋势半窗单一来源)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectionScoreNode implements NodeAction {

    /** 毛利维度满分基准(毛利率 ≥ 30% 记满分) */
    static final BigDecimal FULL_MARGIN_RATIO = new BigDecimal("0.30");

    /** 权重代码默认值(Σ 归一化失败回落;与 sys_config 种子默认一致) */
    private static final BigDecimal DEFAULT_SALES_WEIGHT = new BigDecimal("0.4");
    private static final BigDecimal DEFAULT_TREND_WEIGHT = new BigDecimal("0.3");
    private static final BigDecimal DEFAULT_MARGIN_WEIGHT = new BigDecimal("0.3");

    private final AiRuntimeProperties runtime;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<SelectionCandidate> candidates = (List<SelectionCandidate>) state
                .value(SelectionStateKeys.KEY_CANDIDATES).orElse(List.of());
        if (CollUtil.isEmpty(candidates)) {
            return Map.of(SelectionStateKeys.KEY_SELECTED, List.of());
        }
        Weights weights = Weights.of(runtime);
        int maxQty30 = candidates.stream().mapToInt(SelectionCandidate::qty30).max().orElse(0);
        List<SelectionCandidate> scored = new ArrayList<>(candidates.size());
        for (SelectionCandidate candidate : candidates) {
            scored.add(score(candidate, weights, maxQty30));
        }
        // 综合分降序(稳定排序,同分按 skuId 升序),取前 persistMaxItems 入选
        scored.sort(Comparator.comparing(SelectionCandidate::score).reversed()
                .thenComparing(SelectionCandidate::skuId));
        int limit = Math.max(runtime.selectionPersistMaxItems(), 0);
        List<SelectionCandidate> selected = scored.subList(0, Math.min(limit, scored.size()));
        log.info("选品评分完成:候选 {} 个,入选 {} 个(上限 {},权重 销售={}/趋势={}/毛利={})",
                scored.size(), selected.size(), limit, weights.sales(), weights.trend(), weights.margin());
        return Map.of(SelectionStateKeys.KEY_SELECTED, new ArrayList<>(selected));
    }

    /** 三维评分 + 风险定级单行装配 */
    private SelectionCandidate score(SelectionCandidate candidate, Weights weights, int maxQty30) {
        BigDecimal salesDim = salesDim(candidate.qty30(), maxQty30);
        BigDecimal trendDim = trendDim(candidate.recent7(), candidate.prior7());
        BigDecimal marginDim = marginDim(candidate.margin(), candidate.flags());
        BigDecimal composite = weights.sales().multiply(salesDim)
                .add(weights.trend().multiply(trendDim))
                .add(weights.margin().multiply(marginDim))
                .setScale(1, RoundingMode.HALF_UP);
        List<String> flags = new ArrayList<>(candidate.flags());
        String risk = assessRisk(candidate, flags);
        return candidate.toBuilder()
                .salesDim(salesDim).trendDim(trendDim).marginDim(marginDim)
                .score(composite).risk(risk).flags(flags)
                .build();
    }

    /** 销量规模维度:候选集内相对归一(相对分语义:头部=100,零动销=0) */
    private BigDecimal salesDim(int qty30, int maxQty30) {
        if (maxQty30 <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(qty30)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxQty30), 1, RoundingMode.HALF_UP);
    }

    /** 趋势维度:近 7 天日均 vs 前 7 天日均(见类注公式;日均口径消窗口等长假设) */
    private BigDecimal trendDim(int recent7, int prior7) {
        BigDecimal dim;
        if (recent7 == 0 && prior7 == 0) {
            dim = BigDecimal.valueOf(50);
        } else if (prior7 == 0) {
            dim = BigDecimal.valueOf(100);
        } else if (recent7 == 0) {
            dim = BigDecimal.ZERO;
        } else {
            BigDecimal ratio = BigDecimal.valueOf(recent7)
                    .divide(BigDecimal.valueOf(prior7), 4, RoundingMode.HALF_UP);
            dim = BigDecimal.valueOf(50).add(
                    BigDecimal.valueOf(50).multiply(ratio.subtract(BigDecimal.ONE)));
            dim = clamp(dim);
        }
        return dim.setScale(1, RoundingMode.HALF_UP);
    }

    /** 毛利维度:margin/30% 满分基准;缺失记中性 50(禁猜值,缺口已打标记) */
    private BigDecimal marginDim(BigDecimal margin, List<String> flags) {
        if (margin == null || flags.contains(SelectionCandidate.FLAG_MARGIN_MISSING)) {
            return BigDecimal.valueOf(50).setScale(1);
        }
        return clamp(margin.divide(FULL_MARGIN_RATIO, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))).setScale(1, RoundingMode.HALF_UP);
    }

    /** 风险规则定级(HIGH 须人工复核;标记同步回填 payload 展示用) */
    private String assessRisk(SelectionCandidate candidate, List<String> flags) {
        if (candidate.margin() != null && candidate.margin().signum() < 0) {
            flags.add(SelectionCandidate.FLAG_LOSS_MAKING);
            return AiConsts.RISK_HIGH;
        }
        if (candidate.qtyAvailable() <= 0 && candidate.recent7() > 0) {
            flags.add(SelectionCandidate.FLAG_STOCKOUT);
            return AiConsts.RISK_HIGH;
        }
        if (flags.contains(SelectionCandidate.FLAG_MARGIN_MISSING)) {
            return AiConsts.RISK_MID;
        }
        if (candidate.qty30() == 0) {
            // 零动销候选(有库存滞销):综合分天然垫底,标记随 payload 供人工判读
            flags.add(SelectionCandidate.FLAG_NO_SALES);
            return AiConsts.RISK_MID;
        }
        return AiConsts.RISK_LOW;
    }

    private static BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value.min(BigDecimal.valueOf(100));
    }

    /** 权重三元组(sys_config 覆盖 → 按和归一化;Σ≤0 全量回落默认) */
    record Weights(BigDecimal sales, BigDecimal trend, BigDecimal margin) {

        static Weights of(AiRuntimeProperties runtime) {
            BigDecimal sales = nonNegative(runtime.selectionSalesWeight(), DEFAULT_SALES_WEIGHT);
            BigDecimal trend = nonNegative(runtime.selectionTrendWeight(), DEFAULT_TREND_WEIGHT);
            BigDecimal margin = nonNegative(runtime.selectionMarginWeight(), DEFAULT_MARGIN_WEIGHT);
            BigDecimal sum = sales.add(trend).add(margin);
            if (sum.signum() <= 0) {
                return new Weights(DEFAULT_SALES_WEIGHT, DEFAULT_TREND_WEIGHT, DEFAULT_MARGIN_WEIGHT);
            }
            return new Weights(
                    sales.divide(sum, 6, RoundingMode.HALF_UP),
                    trend.divide(sum, 6, RoundingMode.HALF_UP),
                    margin.divide(sum, 6, RoundingMode.HALF_UP));
        }

        private static BigDecimal nonNegative(BigDecimal value, BigDecimal fallback) {
            return value == null || value.signum() < 0 ? fallback : value;
        }
    }
}
