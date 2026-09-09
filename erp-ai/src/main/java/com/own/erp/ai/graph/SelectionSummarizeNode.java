package com.own.erp.ai.graph;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.config.AiRuntimeProperties;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 摘要节点(#17 智能选品,拍板口径:LLM 只把确定性评分结果写成自然语言推荐理由,
 *     不参与算分——评分可复算,理由可降级):单次调用按 skuId 对齐回填 JSON 数组
 *     [{skuId, summary≤50字}];护栏:入选行超 erp.ai.selection.llm-max-items(runtime 可配)
 *     按综合分降序截断(入选集已按分数排序,直接取前 N 送评),未送评行走模板摘要;
 *     三重降级(apiKey 空/调用失败/解析失败,含 ``` 围栏容错)统一落模板串并置 degraded=true,
 *     工作流照跑照落库——模型故障不阻断建议产出。提示词集中配置(docs/07 §9)
 */
@Slf4j
@Component
public class SelectionSummarizeNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final AiRuntimeProperties runtime;

    /** 模型 api-key 原值(仅判空作降级判定,不落日志/返回体——docs/07 §7 凭证纪律) */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public SelectionSummarizeNode(ChatClient.Builder chatClientBuilder, AiRuntimeProperties runtime) {
        this.chatClient = chatClientBuilder.build();
        this.runtime = runtime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<SelectionCandidate> selected = (List<SelectionCandidate>) state
                .value(SelectionStateKeys.KEY_SELECTED).orElse(List.of());
        Map<Long, String> summaryBySku = summarizeByLlm(selected);
        List<SelectionCandidate> summarized = new ArrayList<>(selected.size());
        boolean degraded = false;
        for (SelectionCandidate candidate : selected) {
            String summary = summaryBySku.get(candidate.skuId());
            if (StrUtil.isBlank(summary)) {
                // LLM 不可用/漏了该行/超出送评护栏:模板串兜底
                summary = templateSummary(candidate);
                degraded = true;
            }
            summarized.add(candidate.toBuilder().summary(summary).llmScored(summaryBySku
                    .containsKey(candidate.skuId())).build());
        }
        return Map.of(SelectionStateKeys.KEY_SELECTED, summarized,
                SelectionStateKeys.KEY_DEGRADED, degraded);
    }

    /** LLM 单次调用产逐 SKU 推荐理由;任何失败返回空 map(调用方逐行模板兜底) */
    private Map<Long, String> summarizeByLlm(List<SelectionCandidate> selected) {
        if (StrUtil.isBlank(apiKey)) {
            log.info("AI 未配置(无 api-key),选品摘要走模板降级");
            return Map.of();
        }
        // 成本护栏:入选集已按综合分降序,直接取前 N 送评,其余走模板(截断行仍产出建议,不阻断)
        int llmMaxItems = runtime.selectionLlmMaxItems();
        List<SelectionCandidate> toSummarize = selected.subList(
                0, Math.min(Math.max(llmMaxItems, 0), selected.size()));
        if (toSummarize.isEmpty()) {
            return Map.of();
        }
        StringBuilder userPrompt = new StringBuilder("请为以下智能选品评分入选的 SKU 各写一句不超过 50 字的中文"
                + "推荐理由(结合评分、销量趋势与毛利说明为什么值得重点关注),只输出 JSON 数组,"
                + "元素形如 {\"skuId\":1,\"summary\":\"...\"},不要输出其他内容:\n");
        for (SelectionCandidate candidate : toSummarize) {
            userPrompt.append(StrUtil.format(
                    "skuId={},商品={},综合评分={},近30天销量={}件,近7天/前7天={}/{}件,毛利率={},可用库存={}件\n",
                    candidate.skuId(),
                    StrUtil.nullToEmpty(candidate.productName()),
                    candidate.score().toPlainString(),
                    candidate.qty30(),
                    candidate.recent7(), candidate.prior7(),
                    candidate.margin() == null ? "未知" : percentText(candidate.margin()),
                    candidate.qtyAvailable()));
        }
        try {
            var response = chatClient.prompt()
                    .system(runtime.selectionPrompt())
                    .user(userPrompt.toString())
                    .call()
                    .chatResponse();
            String text = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return parseSummaries(text);
        } catch (Exception e) {
            log.warn("选品摘要 LLM 调用失败,走模板降级:{}", e.getMessage());
            return Map.of();
        }
    }

    /** 模板摘要(评分与关键事实直给,人工复核入口) */
    private String templateSummary(SelectionCandidate candidate) {
        return StrUtil.format("综合评分 {}:近30天销量 {} 件,毛利率 {},可用库存 {} 件",
                candidate.score().toPlainString(),
                candidate.qty30(),
                candidate.margin() == null ? "未知" : percentText(candidate.margin()),
                candidate.qtyAvailable());
    }

    /** 毛利率百分数文本(0.185 → "18.5%") */
    private String percentText(BigDecimal margin) {
        return margin.multiply(BigDecimal.valueOf(100))
                .setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
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
                    JsonNode idNode = entry.get("skuId");
                    JsonNode summaryNode = entry.get("summary");
                    if (idNode != null && idNode.canConvertToLong() && summaryNode != null
                            && StrUtil.isNotBlank(summaryNode.asText())) {
                        summaryBySku.put(idNode.asLong(), summaryNode.asText());
                    }
                }
            }
            return summaryBySku;
        } catch (Exception e) {
            log.warn("选品摘要回传解析失败,走模板降级 :{}", e.getMessage());
            return Map.of();
        }
    }
}
