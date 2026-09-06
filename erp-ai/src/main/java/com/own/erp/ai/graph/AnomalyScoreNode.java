package com.own.erp.ai.graph;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.ai.constant.AiConsts;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : LLM 评分节点(#6 两段式第二段,只评规则筛出的可疑单控成本):
 *     批量单次调用,JSON 数组回传按 orderId 对齐 [{orderId, riskLevel, reason≤40字}];
 *     输入无 PII(OrderView 收件人/地址/buyer_note 不出契约),无需脱敏。
 *     护栏 llmMaxItems:超限按基线风险降序截断,未送评单直接规则定级且不算降级;
 *     三重降级(apiKey 空/调用失败/解析失败)与逐单漏回/词表外 riskLevel 统一回落规则定级+模板 summary
 *     (选中的单回落置 degraded=true,工作流照跑照落库——评分是"锦上添花",不允许模型故障阻断产出)。
 *     提示词集中 ErpAiProperties(docs/07 §9);无 key 启动不炸、调用时降级(同 ErpChatService 口径);
 *     JSON 解析用 Jackson(Spring 栈自带)
 */
@Slf4j
@Component
public class AnomalyScoreNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> RISK_VOCABULARY = Set.of(
            AiConsts.RISK_LOW, AiConsts.RISK_MID, AiConsts.RISK_HIGH);

    private final ChatClient chatClient;
    private final ErpAiProperties props;

    /** 模型 api-key 原值(仅判空作降级判定,不落日志/返回体——docs/07 §7 凭证纪律) */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public AnomalyScoreNode(ChatClient.Builder chatClientBuilder, ErpAiProperties props) {
        this.chatClient = chatClientBuilder.build();
        this.props = props;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AnomalyItem> items = (List<AnomalyItem>) state.value(AnomalyStateKeys.KEY_ITEMS)
                .orElse(List.of());
        if (items.isEmpty()) {
            // 条件边已挡空,防御兜底:无单不评
            return Map.of(AnomalyStateKeys.KEY_ITEMS, items,
                    AnomalyStateKeys.KEY_DEGRADED, false, AnomalyStateKeys.KEY_LLM_SCORED, 0);
        }
        int llmMaxItems = props.getAnomaly().getLlmMaxItems();
        // 超限截断:基线风险降序(稳定排序,同档保持原序)选入评分,余量直接规则定级
        List<AnomalyItem> ordered = items.stream()
                .sorted(Comparator.comparingInt(
                        (AnomalyItem item) -> AnomalyRule.riskOrder(item.baselineRisk())).reversed())
                .toList();
        List<AnomalyItem> selected = ordered.subList(0, Math.min(llmMaxItems, ordered.size()));
        List<AnomalyItem> truncated = ordered.subList(Math.min(llmMaxItems, ordered.size()), ordered.size());

        Map<Long, LlmScore> scoreByOrder = scoreByLlm(selected);
        boolean degraded = false;
        int llmScored = 0;
        List<AnomalyItem> result = new ArrayList<>(items.size());
        for (AnomalyItem item : selected) {
            LlmScore score = scoreByOrder.get(item.orderId());
            if (score == null) {
                // LLM 不可用或漏了该单/词表外:逐单回落规则定级,选中单回落计降级
                degraded = true;
                result.add(item.toBuilder().summary(templateSummary(item)).llmScored(false).build());
            } else {
                llmScored++;
                result.add(item.toBuilder()
                        .baselineRisk(score.riskLevel()).summary(score.reason()).llmScored(true).build());
            }
        }
        for (AnomalyItem item : truncated) {
            // 未送评单直接规则定级,不算 degraded(护栏语义,拍板口径)
            result.add(item.toBuilder().summary(templateSummary(item)).llmScored(false).build());
        }
        // 回传保持扫描原序,下游落库顺序稳定
        Map<Long, AnomalyItem> byOrder = new HashMap<>();
        result.forEach(item -> byOrder.put(item.orderId(), item));
        List<AnomalyItem> originalOrder = items.stream()
                .map(item -> byOrder.get(item.orderId())).toList();
        return Map.of(AnomalyStateKeys.KEY_ITEMS, originalOrder,
                AnomalyStateKeys.KEY_DEGRADED, degraded, AnomalyStateKeys.KEY_LLM_SCORED, llmScored);
    }

    /** LLM 单次批量评分;任何失败返回空 map(调用方逐单规则回落) */
    private Map<Long, LlmScore> scoreByLlm(List<AnomalyItem> items) {
        if (StrUtil.isBlank(apiKey)) {
            log.info("AI 未配置(无 api-key),订单异常评分走规则回落");
            return Map.of();
        }
        StringBuilder userPrompt = new StringBuilder("待评分可疑订单如下:\n");
        for (AnomalyItem item : items) {
            userPrompt.append(StrUtil.format("orderId={},命中规则={},基线风险={},金额={} {},优惠={},下单时间={},支付时间={}\n",
                    item.orderId(),
                    StrUtil.join("/", item.hitRules().stream().map(AnomalyRule::getLabel).toList()),
                    item.baselineRisk(),
                    item.orderAmount() == null ? "未知" : item.orderAmount().toPlainString(),
                    StrUtil.nullToEmpty(item.currency()),
                    item.discountAmount() == null ? "无" : item.discountAmount().toPlainString(),
                    item.orderTime() == null ? "未知" : item.orderTime(),
                    item.paidTime() == null ? "未支付" : item.paidTime()));
        }
        try {
            var response = chatClient.prompt()
                    .system(props.getAnomaly().getScorePrompt())
                    .user(userPrompt.toString())
                    .call()
                    .chatResponse();
            String text = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return parseScores(text);
        } catch (Exception e) {
            log.warn("订单异常评分 LLM 调用失败,走规则回落 :{}", e.getMessage());
            return Map.of();
        }
    }

    /** 解析模型回传 JSON 数组(容错剥 ``` 代码围栏);解析失败返回空 map。词表外/空理由在逐单对齐时回落 */
    private Map<Long, LlmScore> parseScores(String text) {
        if (StrUtil.isBlank(text)) {
            return Map.of();
        }
        try {
            String cleaned = StrUtil.trim(text);
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1, cleaned.lastIndexOf("```"));
            }
            JsonNode array = OBJECT_MAPPER.readTree(cleaned);
            Map<Long, LlmScore> scoreByOrder = new HashMap<>();
            if (array.isArray()) {
                for (JsonNode entry : array) {
                    JsonNode orderIdNode = entry.get("orderId");
                    JsonNode riskNode = entry.get("riskLevel");
                    JsonNode reasonNode = entry.get("reason");
                    if (orderIdNode == null || !orderIdNode.canConvertToLong()) {
                        continue;
                    }
                    String risk = riskNode == null ? null : StrUtil.trim(riskNode.asText()).toUpperCase();
                    if (risk == null || !RISK_VOCABULARY.contains(risk)
                            || reasonNode == null || StrUtil.isBlank(reasonNode.asText())) {
                        continue;
                    }
                    scoreByOrder.put(orderIdNode.asLong(), new LlmScore(risk, reasonNode.asText()));
                }
            }
            return scoreByOrder;
        } catch (Exception e) {
            log.warn("订单异常评分回传解析失败,走规则回落 :{}", e.getMessage());
            return Map.of();
        }
    }

    /** 规则定级模板摘要(命中规则/基线风险直给,人工复核入口) */
    private String templateSummary(AnomalyItem item) {
        return StrUtil.format("命中规则 {},基线风险 {},建议人工复核",
                StrUtil.join("/", item.hitRules().stream().map(AnomalyRule::getLabel).toList()),
                item.baselineRisk());
    }

    /** LLM 评分回传条目(词表校验通过后才入 map) */
    private record LlmScore(String riskLevel, String reason) {
    }
}
