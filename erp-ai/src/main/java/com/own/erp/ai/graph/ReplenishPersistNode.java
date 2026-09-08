package com.own.erp.ai.graph;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 落库节点(#6 SAA Graph 补货工作流):逐条经 AiSuggestionService.save
 *     (AI 产出唯一入口,必填校验在彼处)写入 ai_suggestion,type=REPLENISH;
 *     风险分级:可用≤0 即缺货 HIGH,否则 MID。不碰任何业务单据(建议只是建议,人工采纳后
 *     走 #10 采购域人工建单,docs/07 §7 AI 只读红线);
 *     payloadJson 优先取 calculate 回填的 V2 算法明细(item.calcJson,含 μ/σ/安全库存/补货点/
 *     目标库存与旧三字段键),空串回落旧三字段形态(防御,正常不触发)
 */
@Component
@RequiredArgsConstructor
public class ReplenishPersistNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiSuggestionService aiSuggestionService;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<ReplenishItem> items = (List<ReplenishItem>) state.value(ReplenishStateKeys.KEY_ITEMS)
                .orElse(List.of());
        for (ReplenishItem item : items) {
            aiSuggestionService.save(AiSuggestion.builder()
                    .suggestionType(AiConsts.TYPE_REPLENISH)
                    .skuId(item.skuId())
                    .refType(AiConsts.REF_TYPE_INVENTORY)
                    .payloadJson(toPayloadJson(item))
                    .summary(item.summary())
                    .riskLevel(item.qtyAvailable() <= 0 ? AiConsts.RISK_HIGH : AiConsts.RISK_MID)
                    .status(AiConsts.STATUS_PENDING)
                    .build());
        }
        return Map.of(ReplenishStateKeys.KEY_PERSISTED, items.size());
    }

    private String toPayloadJson(ReplenishItem item) {
        if (StrUtil.isNotBlank(item.calcJson())) {
            return item.calcJson();
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "qtyAvailable", item.qtyAvailable(),
                    "qtyTransit", item.qtyTransit(),
                    "suggestQty", item.suggestQty()));
        } catch (Exception e) {
            // Map.of 序列化不会失败,兜底防御
            throw new IllegalStateException("补货建议 payload 序列化失败", e);
        }
    }
}
