package com.own.erp.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.ai.service.AiSuggestionService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 落库节点(#6 SAA Graph 订单异常工作流):逐条经 AiSuggestionService.save
 *     (AI 产出唯一入口,必填校验在彼处)写入 ai_suggestion,type=ANOMALY,refType=SHOP_ORDER;
 *     风险等级收条目终值(LLM 定级,降级/未送评为规则基线)。重复 run 产生新一批 = 已接受语义
 *     (同补货);接定时前必须先拍去重语义 → TODO(#6)。不碰任何业务单据
 *     (建议只是建议,人工复核后走页面处理,docs/07 §7 AI 只读红线)
 */
@Component
@RequiredArgsConstructor
public class AnomalyPersistNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AiSuggestionService aiSuggestionService;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AnomalyItem> items = (List<AnomalyItem>) state.value(AnomalyStateKeys.KEY_ITEMS)
                .orElse(List.of());
        for (AnomalyItem item : items) {
            aiSuggestionService.save(AiSuggestion.builder()
                    .suggestionType(AiConsts.TYPE_ANOMALY)
                    .refType(AiConsts.REF_TYPE_SHOP_ORDER)
                    .refId(item.orderId())
                    .shopId(item.shopId())
                    .riskLevel(item.baselineRisk())
                    .payloadJson(toPayloadJson(item))
                    .summary(item.summary())
                    .status(AiConsts.STATUS_PENDING)
                    .build());
        }
        return Map.of(AnomalyStateKeys.KEY_PERSISTED, items.size());
    }

    /** payload 键收口拍板口径;时间序列化为 ISO 字符串(裸 ObjectMapper 无 jsr310,且空值须允许) */
    private String toPayloadJson(AnomalyItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hitRules", item.hitRules().stream().map(AnomalyRule::name).toList());
        payload.put("ruleRisk", item.baselineRisk());
        payload.put("orderAmount", item.orderAmount() == null ? null : item.orderAmount().toPlainString());
        payload.put("currency", item.currency());
        payload.put("exchangeRate", item.exchangeRate() == null ? null : item.exchangeRate().toPlainString());
        payload.put("discountAmount", item.discountAmount() == null ? null : item.discountAmount().toPlainString());
        payload.put("orderTime", formatTime(item.orderTime()));
        payload.put("paidTime", formatTime(item.paidTime()));
        payload.put("llmScored", item.llmScored());
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            // LinkedHashMap+String 序列化不会失败,兜底防御
            throw new IllegalStateException("订单异常 payload 序列化失败", e);
        }
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : DATE_TIME_FORMATTER.format(time);
    }
}
