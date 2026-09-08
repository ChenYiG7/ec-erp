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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 生成节点(#17 三期候选「产品描述生成」V1,LLM 批量单次调用照 AnomalyScoreNode 母本):
 *     逐商品产 listing 文案(标题/五点描述/商品描述/搜索关键词),JSON 数组回传按 productId 对齐;
 *     护栏 llmMaxItems(runtime copy 键,#18 热更):超限按 productId 升序截断(确定序)——
 *     截断商品本轮不产出不算降级,下轮扫描自然补上(去重键=待确认存在)。
 *     ⚠️ 降级语义与补货/异常/采购三工作流刻意不同(拍板记录 devlog):那三处 LLM 只写报告,
 *     建议本体是程序算的,故"模板兜底照落库";文案的本体就是 LLM 产出,无模板可兜——
 *     LLM 不可用/调用失败/解析失败/逐商品漏回或必填字段缺失 → 该商品跳过不产出,degraded=true
 *     零垃圾建议落库。prompt 集中 ErpAiProperties(docs/07 §9);材料不含内部价格字段。
 */
@Slf4j
@Component
public class CopyGenerateNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final AiRuntimeProperties runtime;

    /** 模型 api-key 原值(仅判空作降级判定,不落日志/返回体——docs/07 §7 凭证纪律) */
    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public CopyGenerateNode(ChatClient.Builder chatClientBuilder, AiRuntimeProperties runtime) {
        this.chatClient = chatClientBuilder.build();
        this.runtime = runtime;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<CopyItem> items = (List<CopyItem>) state.value(CopyStateKeys.KEY_ITEMS)
                .orElse(List.of());
        if (items.isEmpty()) {
            // 条件边已挡空,防御兜底:无商品不调模型
            return Map.of(CopyStateKeys.KEY_ITEMS, List.of(),
                    CopyStateKeys.KEY_DEGRADED, false);
        }
        int llmMaxItems = runtime.copyLlmMaxItems();
        // 超限截断:productId 升序(稳定确定序,同轮次可重放),截断商品本轮跳过不算降级(拍板口径)
        List<CopyItem> ordered = items.stream()
                .sorted(Comparator.comparing(CopyItem::productId))
                .toList();
        List<CopyItem> selected = ordered.subList(0, Math.min(llmMaxItems, ordered.size()));
        List<CopyItem> truncated = ordered.subList(Math.min(llmMaxItems, ordered.size()), ordered.size());

        Map<Long, LlmCopy> copyByProduct = generateByLlm(selected);
        boolean degraded = false;
        List<CopyItem> generated = new ArrayList<>(selected.size());
        for (CopyItem item : selected) {
            LlmCopy copy = copyByProduct.get(item.productId());
            if (copy == null) {
                // LLM 不可用/漏回/必填字段缺失:该商品跳过不产出(无模板兜底,拍板语义)
                degraded = true;
                log.info("商品[{}]文案生成未回传或字段缺失,本轮跳过", item.productId());
                continue;
            }
            generated.add(item.toBuilder()
                    .title(copy.title())
                    .bulletPoints(copy.bulletPoints())
                    .description(copy.description())
                    .keywords(copy.keywords())
                    .build());
        }
        log.info("文案生成完成:送评 {} 个,产出 {} 个,截断 {} 个(下轮再生成),degraded={}",
                selected.size(), generated.size(), truncated.size(), degraded);
        return Map.of(CopyStateKeys.KEY_ITEMS, generated,
                CopyStateKeys.KEY_DEGRADED, degraded);
    }

    /** LLM 单次批量生成;任何失败返回空 map(调用方逐商品跳过) */
    private Map<Long, LlmCopy> generateByLlm(List<CopyItem> items) {
        if (StrUtil.isBlank(apiKey)) {
            log.info("AI 未配置(无 api-key),文案生成本轮零产出");
            return Map.of();
        }
        StringBuilder userPrompt = new StringBuilder("请为以下商品各生成一套电商 listing 文案:\n");
        for (CopyItem item : items) {
            userPrompt.append(StrUtil.format("productId={},商品名={},品牌={},类目={},销售属性={},SKU规格={}\n",
                    item.productId(),
                    StrUtil.nullToEmpty(item.productName()),
                    StrUtil.isBlank(item.brandName()) ? "无" : item.brandName(),
                    StrUtil.isBlank(item.categoryName()) ? "无" : item.categoryName(),
                    StrUtil.isBlank(item.attrsJson()) ? "无" : item.attrsJson(),
                    formatSkus(item.skus())));
        }
        try {
            var response = chatClient.prompt()
                    .system(runtime.copyPrompt())
                    .user(userPrompt.toString())
                    .call()
                    .chatResponse();
            String text = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput().getText();
            return parseCopies(text);
        } catch (Exception e) {
            log.warn("文案生成 LLM 调用失败,本轮零产出 :{}", e.getMessage());
            return Map.of();
        }
    }

    /** SKU 规格行紧凑序列化(控制 prompt 体积:sku=ABC01,规格={"颜色":"红"},重量=350g,含电=否) */
    private String formatSkus(List<CopyItem.SkuLine> skus) {
        if (skus == null || skus.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder("[");
        for (CopyItem.SkuLine sku : skus) {
            if (sb.length() > 1) {
                sb.append("; ");
            }
            sb.append(StrUtil.format("sku={},规格={},重量={}g,含电={}",
                    StrUtil.nullToEmpty(sku.skuCode()),
                    StrUtil.isBlank(sku.attrsJson()) ? "无" : sku.attrsJson(),
                    sku.weightG() == null ? "?" : sku.weightG(),
                    sku.battery() != null && sku.battery() == 1 ? "是" : "否"));
        }
        return sb.append(']').toString();
    }

    /**
     * 解析模型回传 JSON 数组(容错剥 ``` 代码围栏);解析失败返回空 map。
     * 逐条校验:productId 必须可解析,title/description 必填非空,bulletPoints/keywords 缺失置空列表
     */
    private Map<Long, LlmCopy> parseCopies(String text) {
        if (StrUtil.isBlank(text)) {
            return Map.of();
        }
        try {
            String cleaned = StrUtil.trim(text);
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(cleaned.indexOf('\n') + 1, cleaned.lastIndexOf("```"));
            }
            JsonNode array = OBJECT_MAPPER.readTree(cleaned);
            Map<Long, LlmCopy> copyByProduct = new HashMap<>();
            if (array.isArray()) {
                for (JsonNode entry : array) {
                    JsonNode idNode = entry.get("productId");
                    JsonNode titleNode = entry.get("title");
                    JsonNode descNode = entry.get("description");
                    if (idNode == null || !idNode.canConvertToLong()
                            || titleNode == null || StrUtil.isBlank(titleNode.asText())
                            || descNode == null || StrUtil.isBlank(descNode.asText())) {
                        continue;
                    }
                    copyByProduct.put(idNode.asLong(), new LlmCopy(
                            titleNode.asText().trim(),
                            readStringList(entry.get("bulletPoints")),
                            descNode.asText().trim(),
                            readStringList(entry.get("keywords"))));
                }
            }
            return copyByProduct;
        } catch (Exception e) {
            log.warn("文案生成回传解析失败,本轮零产出 :{}", e.getMessage());
            return Map.of();
        }
    }

    /** JSON 字符串数组字段读取:缺失/形态不对置空列表,元素逐个取文本跳过非字符串 */
    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            if (element.isTextual() && StrUtil.isNotBlank(element.asText())) {
                values.add(element.asText().trim());
            }
        }
        return List.copyOf(values);
    }

    /** LLM 文案回传条目(必填校验通过后才入 map) */
    private record LlmCopy(String title, List<String> bulletPoints, String description,
                           List<String> keywords) {
    }
}
