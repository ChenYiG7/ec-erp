package com.own.erp.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 落库节点(#17 智能选品):逐行经 AiSuggestionService.save(AI 产出唯一入口,
 *     必填校验在彼处)写入 ai_suggestion,type=SELECTION/refType=GOODS_SKU/refId=skuId/
 *     skuId=skuId/shopId=NULL(SKU 维度全局建议);风险等级取评分节点规则终值。
 *     payloadJson = 评分明细全量(三维分/权重/销量与趋势/毛利/库存/风险标记)——
 *     人工判读与"评分可复算"拍板落地:详情抽屉 JSON 直显可逐项核对(结构化渲染随前端评估)。
 *     重复 run 产生新一批 = 已接受语义(同补货/异常/采购);不碰任何业务表
 *     (建议只是建议,人工采纳后走商品/广告页面,docs/07 §7 AI 只读红线)
 */
@Component
@RequiredArgsConstructor
public class SelectionPersistNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiSuggestionService aiSuggestionService;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<SelectionCandidate> selected = (List<SelectionCandidate>) state
                .value(SelectionStateKeys.KEY_SELECTED).orElse(List.of());
        for (SelectionCandidate candidate : selected) {
            aiSuggestionService.save(AiSuggestion.builder()
                    .suggestionType(AiConsts.TYPE_SELECTION)
                    .refType(AiConsts.REF_TYPE_GOODS_SKU)
                    .refId(candidate.skuId())
                    .skuId(candidate.skuId())
                    .riskLevel(candidate.risk())
                    .payloadJson(toPayloadJson(candidate))
                    .summary(candidate.summary())
                    .status(AiConsts.STATUS_PENDING)
                    .build());
        }
        return Map.of(SelectionStateKeys.KEY_PERSISTED, selected.size());
    }

    /** payload 键收口拍板口径(LinkedHashMap 保字段序;序列化失败即编程期错误上抛) */
    private String toPayloadJson(SelectionCandidate candidate) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("skuId", candidate.skuId());
            payload.put("skuCode", candidate.skuCode());
            payload.put("productName", candidate.productName());
            payload.put("score", candidate.score() == null ? null : candidate.score().toPlainString());
            payload.put("risk", candidate.risk());
            Map<String, Object> dims = new LinkedHashMap<>();
            dims.put("sales", dimText(candidate.salesDim()));
            dims.put("trend", dimText(candidate.trendDim()));
            dims.put("margin", dimText(candidate.marginDim()));
            payload.put("dims", dims);
            payload.put("qty30", candidate.qty30());
            payload.put("recent7", candidate.recent7());
            payload.put("prior7", candidate.prior7());
            payload.put("qtyAvailable", candidate.qtyAvailable());
            payload.put("qtyTransit", candidate.qtyTransit());
            payload.put("salesCny", candidate.salesCny() == null ? null : candidate.salesCny().toPlainString());
            payload.put("profitCny", candidate.profitCny() == null ? null : candidate.profitCny().toPlainString());
            payload.put("margin", candidate.margin() == null ? null : candidate.margin().toPlainString());
            payload.put("flags", candidate.flags() == null ? List.of() : candidate.flags());
            payload.put("llmScored", candidate.llmScored());
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("选品建议 payload 序列化失败", e);
        }
    }

    private static String dimText(BigDecimal dim) {
        return dim == null ? null : dim.toPlainString();
    }
}
