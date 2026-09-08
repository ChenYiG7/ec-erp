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
 * @Description : 落库节点(#17 SAA Graph 采购建议工作流):逐组经 AiSuggestionService.save
 *     (AI 产出唯一入口,必填校验在彼处)写入 ai_suggestion,type=PURCHASE/refType=SUPPLIER/
 *     refId=supplierId/skuId=NULL(组级建议,明细行在 payloadJson);
 *     风险分级:组内含缺货 SKU(可用≤0)即 HIGH,否则 MID。
 *     不碰任何采购单据(落位表拍板:只产建议进 ai_suggestion,人工采纳后走 #10 采购域人工建单,
 *     docs/07 §7 AI 只读红线)
 */
@Component
@RequiredArgsConstructor
public class PurchasePersistNode implements NodeAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiSuggestionService aiSuggestionService;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<PurchaseGroup> groups = (List<PurchaseGroup>) state.value(PurchaseStateKeys.KEY_GROUPS)
                .orElse(List.of());
        for (PurchaseGroup group : groups) {
            aiSuggestionService.save(AiSuggestion.builder()
                    .suggestionType(AiConsts.TYPE_PURCHASE)
                    .refType(AiConsts.REF_TYPE_SUPPLIER)
                    .refId(group.supplierId())
                    .payloadJson(toPayloadJson(group))
                    .summary(group.summary())
                    .riskLevel(group.hasStockout() ? AiConsts.RISK_HIGH : AiConsts.RISK_MID)
                    .status(AiConsts.STATUS_PENDING)
                    .build());
        }
        return Map.of(PurchaseStateKeys.KEY_PERSISTED, groups.size());
    }

    /** payloadJson:组级汇总 + 行明细(LinkedHashMap 保字段序;序列化失败即编程期错误上抛) */
    private String toPayloadJson(PurchaseGroup group) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("supplierId", group.supplierId());
            payload.put("supplierName", group.supplierName());
            payload.put("totalQty", group.totalQty());
            payload.put("estAmount", group.estAmount().toPlainString());
            List<Map<String, Object>> lines = new java.util.ArrayList<>(group.lines().size());
            for (PurchaseGroup.Line line : group.lines()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("skuId", line.skuId());
                row.put("suggestQty", line.suggestQty());
                row.put("lastPrice", line.lastPrice() == null ? null : line.lastPrice().toPlainString());
                row.put("estAmount", line.estAmount().toPlainString());
                row.put("qtyAvailable", line.qtyAvailable());
                lines.add(row);
            }
            payload.put("lines", lines);
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("采购建议 payload 序列化失败", e);
        }
    }
}
