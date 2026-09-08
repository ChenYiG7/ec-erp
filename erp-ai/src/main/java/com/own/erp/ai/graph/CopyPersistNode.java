package com.own.erp.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 落库节点(#17 三期候选「产品描述生成」V1):逐商品经 AiSuggestionService.save
 *     (AI 产出唯一入口,必填校验在彼处)写入 ai_suggestion,type=COPYWRITING(词表既留槽位)/
 *     refType=GOODS_PRODUCT/refId=productId/skuId=NULL/shopId=NULL(商品级建议,文案四件在 payloadJson);
 *     summary = 建议标题(列表直显即产出主件);风险恒 LOW(纯文案产出无资金/履约风险,采纳后人工复核再用于上架);
 *     不碰商品表与平台 listing(docs/07 §7 AI 只读红线):采纳仅确认,文案文本在 payloadJson
 *     供前端复制,自动回填平台随 adapter 上架/改写接口扩容再评估(V1 拍板,记录 devlog)
 */
@Component
@RequiredArgsConstructor
public class CopyPersistNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiSuggestionService aiSuggestionService;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<CopyItem> items = (List<CopyItem>) state.value(CopyStateKeys.KEY_ITEMS)
                .orElse(List.of());
        for (CopyItem item : items) {
            aiSuggestionService.save(AiSuggestion.builder()
                    .suggestionType(AiConsts.TYPE_COPYWRITING)
                    .refType(AiConsts.REF_TYPE_GOODS_PRODUCT)
                    .refId(item.productId())
                    .payloadJson(toPayloadJson(item))
                    .summary(item.title())
                    .riskLevel(AiConsts.RISK_LOW)
                    .status(AiConsts.STATUS_PENDING)
                    .build());
        }
        return Map.of(CopyStateKeys.KEY_PERSISTED, items.size());
    }

    /** payloadJson:文案四件(LinkedHashMap 保字段序;序列化失败即编程期错误上抛) */
    private String toPayloadJson(CopyItem item) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", item.productId());
            payload.put("productName", item.productName());
            payload.put("title", item.title());
            payload.put("bulletPoints", item.bulletPoints() == null ? List.of() : item.bulletPoints());
            payload.put("description", item.description());
            payload.put("keywords", item.keywords() == null ? List.of() : item.keywords());
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("文案建议 payload 序列化失败", e);
        }
    }
}
