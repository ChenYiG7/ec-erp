package com.own.erp.ai.graph;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 摘要节点(#6 SAA Graph 补货工作流,拍板口径:LLM 只把计算结果写成自然语言报告):
 *     单次调用把逐 SKU 建议量写成一句话中文摘要,约定 JSON 数组回传按 skuId 对齐回填;
 *     三重降级(apiKey 空/调用失败/解析失败)统一落模板串并置 degraded=true,工作流照跑照落库——
 *     摘要是"锦上添花",不允许模型故障阻断建议产出。提示词集中 ErpAiProperties(docs/07 §9),
 *     无 key 启动不炸、调用时降级(同 ErpChatService 口径);JSON 解析用 Jackson(Spring 栈自带)
 */
@Slf4j
@Component
public class ReplenishSummarizeNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final AiRuntimeProperties runtime;

    /** 模型 api-key 原值(仅判空作降级判定,不落日志/返回体——docs/07 §7 凭证纪律) */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public ReplenishSummarizeNode(ChatClient.Builder chatClientBuilder, AiRuntimeProperties runtime) {
        this.chatClient = chatClientBuilder.build();
        this.runtime = runtime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<ReplenishItem> items = (List<ReplenishItem>) state.value(ReplenishStateKeys.KEY_ITEMS)
                .orElse(List.of());
        Map<Long, String> summaryBySku = summarizeByLlm(items);
        List<ReplenishItem> summarized = new ArrayList<>(items.size());
        boolean degraded = false;
        for (ReplenishItem item : items) {
            String summary = summaryBySku.get(item.skuId());
            if (StrUtil.isBlank(summary)) {
                // LLM 不可用或漏了该 SKU:模板串兜底
                summary = StrUtil.format("SKU {} 可用 {},在途 {},建议补货 {}",
                        item.skuId(), item.qtyAvailable(), item.qtyTransit(), item.suggestQty());
                degraded = true;
            }
            summarized.add(item.toBuilder().summary(summary).build());
        }
        return Map.of(ReplenishStateKeys.KEY_ITEMS, summarized, ReplenishStateKeys.KEY_DEGRADED, degraded);
    }

    /** LLM 单次调用产逐 SKU 摘要;任何失败返回空 map(调用方逐条模板兜底) */
    private Map<Long, String> summarizeByLlm(List<ReplenishItem> items) {
        if (StrUtil.isBlank(apiKey)) {
            log.info("AI 未配置(无 api-key),补货摘要走模板降级");
            return Map.of();
        }
        StringBuilder userPrompt = new StringBuilder("请为以下补货建议各写一句不超过 40 字的中文摘要(说明为什么补、补多少),"
                + "只输出 JSON 数组,元素形如 {\"skuId\":1,\"summary\":\"...\"},不要输出其他内容:\n");
        for (ReplenishItem item : items) {
            userPrompt.append(StrUtil.format("skuId={},可用={},在途={},建议补货={}\n",
                    item.skuId(), item.qtyAvailable(), item.qtyTransit(), item.suggestQty()));
        }
        try {
            var response = chatClient.prompt()
                    .system(runtime.replenishSummaryPrompt())
                    .user(userPrompt.toString())
                    .call()
                    .chatResponse();
            String text = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return parseSummaries(text);
        } catch (Exception e) {
            log.warn("补货摘要 LLM 调用失败,走模板降级 :{}", e.getMessage());
            return Map.of();
        }
    }

    /** 解析模型回传 JSON 数组(容错剥 ``` 代码围栏);解析失败返回空 map */
    private Map<Long, String> parseSummaries(String text) {
        if (StrUtil.isBlank(text)) {
            return Map.of();
        }
        try {
            String cleaned = StrUtil.trim(text);
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1, cleaned.lastIndexOf("```"));
            }
            JsonNode array = OBJECT_MAPPER.readTree(cleaned);
            Map<Long, String> summaryBySku = new HashMap<>();
            if (array.isArray()) {
                for (JsonNode entry : array) {
                    JsonNode skuIdNode = entry.get("skuId");
                    JsonNode summaryNode = entry.get("summary");
                    if (skuIdNode != null && skuIdNode.canConvertToLong() && summaryNode != null
                            && StrUtil.isNotBlank(summaryNode.asText())) {
                        summaryBySku.put(skuIdNode.asLong(), summaryNode.asText());
                    }
                }
            }
            return summaryBySku;
        } catch (Exception e) {
            log.warn("补货摘要回传解析失败,走模板降级 :{}", e.getMessage());
            return Map.of();
        }
    }
}
