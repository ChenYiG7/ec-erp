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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 摘要节点(#17 SAA Graph 采购建议工作流,拍板口径:LLM 只把聚合结果写成自然语言报告):
 *     单次调用把逐供应商采购计划写成一句中文摘要,约定 JSON 数组回传按 supplierId 对齐回填;
 *     护栏:供应商组超 erp.ai.purchase.llm-max-items(runtime 可配)按预估金额降序截断,
 *     未送评组走模板摘要;三重降级(apiKey 空/调用失败/解析失败,含 ``` 围栏容错)统一落模板串
 *     并置 degraded=true,工作流照跑照落库——模型故障不阻断建议产出。提示词集中配置(docs/07 §9)
 */
@Slf4j
@Component
public class PurchaseSummarizeNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final AiRuntimeProperties runtime;

    /** 模型 api-key 原值(仅判空作降级判定,不落日志/返回体——docs/07 §7 凭证纪律) */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public PurchaseSummarizeNode(ChatClient.Builder chatClientBuilder, AiRuntimeProperties runtime) {
        this.chatClient = chatClientBuilder.build();
        this.runtime = runtime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<PurchaseGroup> groups = (List<PurchaseGroup>) state.value(PurchaseStateKeys.KEY_GROUPS)
                .orElse(List.of());
        Map<Long, String> summaryBySupplier = summarizeByLlm(groups);
        List<PurchaseGroup> summarized = new ArrayList<>(groups.size());
        boolean degraded = false;
        for (PurchaseGroup group : groups) {
            String summary = summaryBySupplier.get(group.supplierId());
            if (StrUtil.isBlank(summary)) {
                // LLM 不可用/漏了该组/超出送评护栏:模板串兜底
                summary = StrUtil.format("供应商 {}:建议采购 {} 个 SKU 共 {} 件,预估金额 {}",
                        group.supplierName() == null ? group.supplierId() : group.supplierName(),
                        group.lines().size(), group.totalQty(), group.estAmount().toPlainString());
                degraded = true;
            }
            summarized.add(group.toBuilder().summary(summary).build());
        }
        return Map.of(PurchaseStateKeys.KEY_GROUPS, summarized, PurchaseStateKeys.KEY_DEGRADED, degraded);
    }

    /** LLM 单次调用产逐供应商摘要;任何失败返回空 map(调用方逐组模板兜底) */
    private Map<Long, String> summarizeByLlm(List<PurchaseGroup> groups) {
        if (StrUtil.isBlank(apiKey)) {
            log.info("AI 未配置(无 api-key),采购摘要走模板降级");
            return Map.of();
        }
        // 成本护栏:按预估金额降序送评前 N 组,其余走模板(截断组仍产出建议,不阻断)
        int llmMaxItems = runtime.purchaseLlmMaxItems();
        List<PurchaseGroup> toScore = groups.stream()
                .sorted(Comparator.comparing(PurchaseGroup::estAmount).reversed())
                .limit(Math.max(llmMaxItems, 0))
                .toList();
        if (toScore.isEmpty()) {
            return Map.of();
        }
        StringBuilder userPrompt = new StringBuilder("请为以下按供应商聚合的采购计划各写一句不超过 50 字的中文摘要"
                + "(说明采购理由与紧急程度),只输出 JSON 数组,元素形如 {\"supplierId\":1,\"summary\":\"...\"},"
                + "不要输出其他内容:\n");
        for (PurchaseGroup group : toScore) {
            userPrompt.append(StrUtil.format("supplierId={},供应商={},SKU明细={},总件数={},预估金额={}\n",
                    group.supplierId(),
                    group.supplierName() == null ? "未知" : group.supplierName(),
                    formatLines(group.lines()),
                    group.totalQty(),
                    group.estAmount().toPlainString()));
        }
        try {
            var response = chatClient.prompt()
                    .system(runtime.purchaseSummaryPrompt())
                    .user(userPrompt.toString())
                    .call()
                    .chatResponse();
            String text = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return parseSummaries(text);
        } catch (Exception e) {
            log.warn("采购摘要 LLM 调用失败,走模板降级:{}", e.getMessage());
            return Map.of();
        }
    }

    /** 行明细紧凑序列化(控制 prompt 体积:sku=1,qty=10,price=12.5,amount=125,available=0) */
    private String formatLines(List<PurchaseGroup.Line> lines) {
        StringBuilder sb = new StringBuilder("[");
        for (PurchaseGroup.Line line : lines) {
            if (sb.length() > 1) {
                sb.append("; ");
            }
            sb.append(StrUtil.format("sku={},qty={},price={},amount={},available={}",
                    line.skuId(), line.suggestQty(),
                    line.lastPrice() == null ? "0" : line.lastPrice().toPlainString(),
                    line.estAmount().toPlainString(),
                    line.qtyAvailable()));
        }
        return sb.append(']').toString();
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
            Map<Long, String> summaryBySupplier = new HashMap<>();
            if (array.isArray()) {
                for (JsonNode entry : array) {
                    JsonNode idNode = entry.get("supplierId");
                    JsonNode summaryNode = entry.get("summary");
                    if (idNode != null && idNode.canConvertToLong() && summaryNode != null
                            && StrUtil.isNotBlank(summaryNode.asText())) {
                        summaryBySupplier.put(idNode.asLong(), summaryNode.asText());
                    }
                }
            }
            return summaryBySupplier;
        } catch (Exception e) {
            log.warn("采购摘要回传解析失败,走模板降级:{}", e.getMessage());
            return Map.of();
        }
    }
}
