package com.own.erp.ai.graph;

import com.own.erp.ai.config.AiRuntimeProperties;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.common.constant.ConfigConsts;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : SelectionScoreNode 单测(#17 智能选品,AIR:纯程序评分,OverAllState 真实装配):
 *     三维维度分逐项手算断言(销量相对归一/趋势四分支/毛利满分基准与缺失中性)+
 *     权重归一化与 Σ≤0 回落 + 风险规则定级全分支 + topN 截断与同分 skuId 稳定排序
 */
class SelectionScoreNodeTest {

    private SelectionScoreNode nodeWith(Map<String, String> overrides) {
        ErpAiProperties props = new ErpAiProperties();
        AiRuntimeProperties runtime = overrides == null
                ? RuntimePropsStub.of(props)
                : RuntimePropsStub.of(props, overrides);
        return new SelectionScoreNode(runtime);
    }

    private OverAllState stateOf(SelectionCandidate... candidates) {
        OverAllState state = new OverAllState();
        state.registerKeyAndStrategy(SelectionStateKeys.KEY_CANDIDATES, KeyStrategy.REPLACE);
        state.input(Map.of(SelectionStateKeys.KEY_CANDIDATES, List.of(candidates)));
        return state;
    }

    /** 标准健康候选:销量窗口内最大 200/近7=前7=14(趋势持平)/毛利 30%(满分基准) */
    private SelectionCandidate healthy(Long skuId, int qty30, int maxQty30) {
        return SelectionCandidate.builder()
                .skuId(skuId).skuCode("C" + skuId).productName("商品" + skuId)
                .qtyAvailable(50).qtyTransit(0)
                .qty30(qty30).recent7(qty30 / 10).prior7(qty30 / 10)
                .salesCny(new BigDecimal("1000")).profitCny(new BigDecimal("300"))
                .margin(new BigDecimal("0.3000"))
                .flags(new ArrayList<>()).summary("").llmScored(false)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultWeightsHandComputedScores() throws Exception {
        // maxQty30=200:A=200 销量 100 分 / B=100 销量 50 分;A 持平趋势 50、毛利 30% 满分 100
        // 综合分 A = 0.4×100 + 0.3×50 + 0.3×100 = 85.0;B = 0.4×50 + 0.3×50 + 0.3×100 = 65.0
        SelectionCandidate a = healthy(1L, 200, 200);
        SelectionCandidate b = healthy(2L, 100, 200);
        Map<String, Object> result = nodeWith(null).apply(stateOf(a, b));
        List<SelectionCandidate> selected = (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertEquals(2, selected.size());
        assertEquals(new BigDecimal("85.0"), selected.get(0).score());
        assertEquals(new BigDecimal("65.0"), selected.get(1).score());
        assertEquals(new BigDecimal("100.0"), selected.get(0).salesDim());
        assertEquals(new BigDecimal("50.0"), selected.get(1).salesDim());
        assertEquals(new BigDecimal("50.0"), selected.get(0).trendDim());
        assertEquals(new BigDecimal("100.0"), selected.get(0).marginDim());
    }

    @Test
    @SuppressWarnings("unchecked")
    void weightsNormalizedBySumAndZeroSumFallsBackToDefaults() throws Exception {
        // 覆盖 0.2/0.3/0.5 → 归一化后即原值;综合分 = 0.2×100+0.3×50+0.5×100 = 85.0(与默认巧合同值,
        // 换销量维度区分:B 销量 50 → 0.2×50+0.3×50+0.5×100 = 75.0)
        SelectionCandidate a = healthy(1L, 200, 200);
        SelectionCandidate b = healthy(2L, 100, 200);
        Map<String, String> overrides = Map.of(
                ConfigConsts.KEY_SELECTION_SALES_WEIGHT, "0.2",
                ConfigConsts.KEY_SELECTION_TREND_WEIGHT, "0.3",
                ConfigConsts.KEY_SELECTION_MARGIN_WEIGHT, "0.5");
        Map<String, Object> result = nodeWith(overrides).apply(stateOf(a, b));
        List<SelectionCandidate> selected = (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertEquals(new BigDecimal("85.0"), selected.get(0).score());
        assertEquals(new BigDecimal("75.0"), selected.get(1).score());

        // Σ=0(全 0)→ 回落代码默认 0.4/0.3/0.3(与 defaultWeightsHandComputedScores 同期望)
        Map<String, String> zero = Map.of(
                ConfigConsts.KEY_SELECTION_SALES_WEIGHT, "0",
                ConfigConsts.KEY_SELECTION_TREND_WEIGHT, "0",
                ConfigConsts.KEY_SELECTION_MARGIN_WEIGHT, "0");
        Map<String, Object> fallback = nodeWith(zero).apply(stateOf(a));
        List<SelectionCandidate> selected2 = (List<SelectionCandidate>) fallback.get(SelectionStateKeys.KEY_SELECTED);
        assertEquals(new BigDecimal("85.0"), selected2.get(0).score());
    }

    @Test
    @SuppressWarnings("unchecked")
    void trendFourBranches() throws Exception {
        // 涨:recent=prior×2 → ratio=2 → ratio×50=100;腰斩 ratio=0.5 → 25;
        // prior=0 且 recent>0(新起量)→ 100;recent=0 且 prior>0(停卖)→ 0
        SelectionCandidate up = healthy(1L, 100, 100).toBuilder().recent7(14).prior7(7).build();
        SelectionCandidate down = healthy(2L, 100, 100).toBuilder().recent7(7).prior7(14).build();
        SelectionCandidate rising = healthy(3L, 100, 100).toBuilder().recent7(7).prior7(0).build();
        SelectionCandidate stopped = healthy(4L, 100, 100).toBuilder().recent7(0).prior7(7).build();
        Map<String, Object> result = nodeWith(null)
                .apply(stateOf(up, down, rising, stopped));
        List<SelectionCandidate> selected = (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertEquals(new BigDecimal("100.0"), findBySku(selected, 1L).trendDim());
        assertEquals(new BigDecimal("25.0"), findBySku(selected, 2L).trendDim());
        assertEquals(new BigDecimal("100.0"), findBySku(selected, 3L).trendDim());
        assertEquals(new BigDecimal("0.0"), findBySku(selected, 4L).trendDim());
    }

    @Test
    @SuppressWarnings("unchecked")
    void marginMissingIsNeutralAndNegativeClampsToZero() throws Exception {
        // 缺毛利(MARGIN_MISSING)→ 中性 50;负毛利 → 钳 0(仍参与综合分,风险走 HIGH)
        SelectionCandidate missing = healthy(1L, 100, 100).toBuilder()
                .margin(null).flags(new ArrayList<>(List.of(SelectionCandidate.FLAG_MARGIN_MISSING)))
                .build();
        SelectionCandidate loss = healthy(2L, 100, 100).toBuilder()
                .margin(new BigDecimal("-0.0500")).build();
        Map<String, Object> result = nodeWith(null).apply(stateOf(missing, loss));
        List<SelectionCandidate> selected = (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertEquals(new BigDecimal("50.0"), findBySku(selected, 1L).marginDim());
        assertEquals(new BigDecimal("0.0"), findBySku(selected, 2L).marginDim());
    }

    @Test
    @SuppressWarnings("unchecked")
    void riskBranches() throws Exception {
        // 亏损在卖 HIGH(LOSS_MAKING)/ 断货+近期动销 HIGH(STOCKOUT)/ 缺毛利 MID / 零动销 MID(NO_SALES)
        SelectionCandidate loss = healthy(1L, 100, 100).toBuilder()
                .margin(new BigDecimal("-0.0500")).build();
        SelectionCandidate stockout = healthy(2L, 100, 100).toBuilder()
                .qtyAvailable(0).recent7(7).build();
        SelectionCandidate missing = healthy(3L, 100, 100).toBuilder()
                .margin(null).flags(new ArrayList<>(List.of(SelectionCandidate.FLAG_MARGIN_MISSING)))
                .build();
        SelectionCandidate noSales = healthy(4L, 0, 100).toBuilder()
                .qty30(0).recent7(0).prior7(0).build();
        Map<String, Object> result = nodeWith(null)
                .apply(stateOf(loss, stockout, missing, noSales));
        List<SelectionCandidate> selected = (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertEquals("HIGH", findBySku(selected, 1L).risk());
        assertTrue(findBySku(selected, 1L).flags().contains(SelectionCandidate.FLAG_LOSS_MAKING));
        assertEquals("HIGH", findBySku(selected, 2L).risk());
        assertTrue(findBySku(selected, 2L).flags().contains(SelectionCandidate.FLAG_STOCKOUT));
        assertEquals("MID", findBySku(selected, 3L).risk());
        assertEquals("MID", findBySku(selected, 4L).risk());
        assertTrue(findBySku(selected, 4L).flags().contains(SelectionCandidate.FLAG_NO_SALES));
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthyCandidateIsLowRisk() throws Exception {
        Map<String, Object> result = nodeWith(null).apply(stateOf(healthy(1L, 200, 200)));
        List<SelectionCandidate> selected = (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertEquals("LOW", selected.get(0).risk());
    }

    @Test
    @SuppressWarnings("unchecked")
    void persistLimitTruncatesAndTieBreaksBySkuId() throws Exception {
        ErpAiProperties props = new ErpAiProperties();
        props.getSelection().setPersistMaxItems(2);
        SelectionScoreNode node = new SelectionScoreNode(RuntimePropsStub.of(props));
        // 三行完全同分(同销量同趋势同毛利)→ 同分按 skuId 升序取前 2
        Map<String, Object> result = node.apply(stateOf(
                healthy(3L, 100, 100), healthy(1L, 100, 100), healthy(2L, 100, 100)));
        List<SelectionCandidate> selected = (List<SelectionCandidate>) result.get(SelectionStateKeys.KEY_SELECTED);
        assertEquals(2, selected.size());
        assertEquals(1L, selected.get(0).skuId());
        assertEquals(2L, selected.get(1).skuId());
    }

    private SelectionCandidate findBySku(List<SelectionCandidate> list, Long skuId) {
        return list.stream().filter(c -> skuId.equals(c.skuId())).findFirst().orElseThrow();
    }
}
