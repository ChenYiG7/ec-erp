package com.own.erp.ai.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.ai.constant.AiConsts;
import com.own.erp.ai.entity.AiSuggestion;
import com.own.erp.contract.InventoryQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026-9-12
 * @Description : 自动拆单建议服务(#29 余量,2026-09-12 方案 A 纯规则拍板,零 LLM 成本):
 *     订单审核通过后(erp-order OrderReviewedEvent 经 erp-api 监听器桥接,铁律 2)按
 *     "SKU→最优发货仓"分组——取各 SKU 可用>0 且可用量最大的仓(并列取仓 ID 小者,确定性);
 *     分组结果涉及 ≥2 个仓时落 ai_suggestion(type=SPLIT_ADVICE,refType=SHOP_ORDER)供人工确认,
 *     单仓可整单履约不产建议(无噪音);无可用库存的行在摘要注明(不猜仓,人工决策)。
 *     建议只是建议:建单执行仍走发货单页面(#29 拆单增强执行面),AI 只读红线不变。
 *     入参中立 record(本服务自有模型,不引 erp-order 类型);取数走只读契约(InventoryQueryApi)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SplitAdviceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 单 SKU 仓候选扫描量(契约钳制 ≤100;仓数量级远小于此,单页足够) */
    private static final int WAREHOUSE_SCAN_SIZE = 100;

    private final @Lazy InventoryQueryApi inventoryQueryApi;
    private final AiSuggestionService aiSuggestionService;

    /** 建议入参(中立 record,erp-api 监听器从 OrderReviewedEvent 显式映射) */
    public record AdviceInput(Long orderId, Long shopId, String platformOrderId, List<Item> items) {

        /** 明细项:SKU + 数量 */
        public record Item(Long skuId, Integer quantity) {
        }
    }

    /** 拆单分组行(payloadJson lines 键收口) */
    private record GroupRow(Long skuId, Integer quantity, Long warehouseId) {
    }

    /**
     * 产出拆单建议:单仓可整单履约(或全无库存)不产;需多仓时落一条聚合建议
     * (审核通过一次 cas 保证事件至多一次,无重复建议问题)
     */
    public void advise(AdviceInput input) {
        if (input == null || input.items() == null || input.items().isEmpty()) {
            return;
        }
        Map<Long, List<GroupRow>> byWarehouse = new LinkedHashMap<>();
        List<String> noStock = new ArrayList<>();
        for (AdviceInput.Item item : input.items()) {
            Long warehouseId = pickBestWarehouse(item.skuId());
            GroupRow row = new GroupRow(item.skuId(), item.quantity(), warehouseId);
            if (warehouseId == null) {
                noStock.add("SKU " + item.skuId());
                continue;
            }
            byWarehouse.computeIfAbsent(warehouseId, k -> new ArrayList<>()).add(row);
        }
        if (byWarehouse.size() < 2) {
            // 单仓可整单履约(或全部缺货):无需拆单,不产建议
            log.debug("拆单建议跳过(无需多仓履约) orderId={} 仓组数={}", input.orderId(), byWarehouse.size());
            return;
        }
        List<GroupRow> lines = byWarehouse.values().stream().flatMap(List::stream).toList();
        String summary = buildSummary(input, byWarehouse, noStock);
        saveSuggestion(input, summary, lines, noStock);
        log.info("拆单建议已落库 orderId={} 仓组={} 行={}", input.orderId(), byWarehouse.size(), lines.size());
    }

    /** SKU→最优发货仓:可用>0 中取可用量最大(并列取仓 ID 小者,确定性);无可用返 null */
    private Long pickBestWarehouse(Long skuId) {
        if (skuId == null) {
            return null;
        }
        Long best = null;
        int bestQty = 0;
        for (InventoryQueryApi.InventoryView row : scanSkuInventory(skuId)) {
            int available = row.qtyAvailable() == null ? 0 : row.qtyAvailable();
            if (available <= 0) {
                continue;
            }
            if (available > bestQty || (available == bestQty && best != null && row.warehouseId() < best)) {
                best = row.warehouseId();
                bestQty = available;
            }
        }
        return best;
    }

    /** 单 SKU 库存行扫描(InventoryFilter skuId 精确,单页 100 覆盖仓数量级) */
    private List<InventoryQueryApi.InventoryView> scanSkuInventory(Long skuId) {
        return inventoryQueryApi.pageInventory(InventoryQueryApi.InventoryFilter.builder()
                .skuId(skuId).pageNo(1).pageSize(WAREHOUSE_SCAN_SIZE).build()).list();
    }

    /** 摘要:按仓分组行数 + 缺货提示(禁买家 PII,只 SKU/仓/数量) */
    private String buildSummary(AdviceInput input, Map<Long, List<GroupRow>> byWarehouse, List<String> noStock) {
        List<String> parts = new ArrayList<>();
        byWarehouse.forEach((warehouseId, rows) ->
                parts.add(StrUtil.format("仓 {} 发 {} 行", warehouseId, rows.size())));
        if (!noStock.isEmpty()) {
            parts.add("无可用库存:" + String.join("/", noStock));
        }
        return StrUtil.format("订单 {} 建议按仓拆单发货:{}", input.platformOrderId(), String.join("; ", parts));
    }

    /** 落库:payload 键收口(warehouseGroups 分仓行 + noStockSkus);save 必填校验在 AiSuggestionService */
    private void saveSuggestion(AdviceInput input, String summary, List<GroupRow> lines, List<String> noStock) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("warehouseGroups", lines.stream()
                    .map(row -> Map.of("skuId", row.skuId(), "quantity", row.quantity(), "warehouseId", row.warehouseId()))
                    .toList());
            payload.put("noStockSkus", noStock);
            aiSuggestionService.save(AiSuggestion.builder()
                    .suggestionType(AiConsts.TYPE_SPLIT_ADVICE)
                    .refType(AiConsts.REF_TYPE_SHOP_ORDER)
                    .refId(input.orderId())
                    .shopId(input.shopId())
                    .payloadJson(OBJECT_MAPPER.writeValueAsString(payload))
                    .summary(summary)
                    .status(AiConsts.STATUS_PENDING)
                    .build());
        } catch (Exception e) {
            // payload 序列化/落库失败不外抛:建议属旁路,失败日志可查
            log.error("拆单建议落库失败 orderId={}", input.orderId(), e);
        }
    }
}
