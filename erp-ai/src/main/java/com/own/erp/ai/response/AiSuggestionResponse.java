package com.own.erp.ai.response;

import com.own.erp.ai.entity.AiSuggestion;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI建议表对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进组件即编译期封死
 */
@Builder
public record AiSuggestionResponse(

        /** 主键 */
        Long id,

        /** 建议类型:REPLENISH补货/PRICING定价/ANOMALY异常/COPYWRITING文案(封闭词表,随AI服务扩容) */
        String suggestionType,

        /** 关联店铺ID(shop.id,跨店/全局建议为NULL) */
        Long shopId,

        /** 关联内部SKU ID(product_sku.id,非SKU维度建议为NULL) */
        Long skuId,

        /** 关联业务类型(如SHOP_ORDER/INVENTORY,对齐inventory_flow.biz_type风格) */
        String refType,

        /** 关联业务单据ID */
        Long refId,

        /** 建议结构化负载(补货量/建议价等,展示与采纳回放用) */
        String payloadJson,

        /** 建议摘要(列表直显,LLM结论一句话) */
        String summary,

        /** 风险等级:LOW/MID/HIGH(HIGH须人工复核) */
        String riskLevel,

        /** 确认状态:0待确认/1已采纳/2已忽略(人工确认后走业务接口,AI禁直接写业务表) */
        Integer status,

        /** 确认人(sys_user.id) */
        Long confirmedBy,

        /** 确认时间 */
        LocalDateTime confirmedAt,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static AiSuggestionResponse from(AiSuggestion entity) {
        return AiSuggestionResponse.builder()
                .id(entity.getId())
                .suggestionType(entity.getSuggestionType())
                .shopId(entity.getShopId())
                .skuId(entity.getSkuId())
                .refType(entity.getRefType())
                .refId(entity.getRefId())
                .payloadJson(entity.getPayloadJson())
                .summary(entity.getSummary())
                .riskLevel(entity.getRiskLevel())
                .status(entity.getStatus())
                .confirmedBy(entity.getConfirmedBy())
                .confirmedAt(entity.getConfirmedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
