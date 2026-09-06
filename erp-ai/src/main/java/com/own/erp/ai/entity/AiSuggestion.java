package com.own.erp.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI建议表(AI产出一律落此表,人工确认后走正常业务接口)(ai_suggestion)
 */
/**
 * @Data+@Builder 默认四注解(docs/07 §1 分级①):@Data 供 MP 反射映射与读改写/写前回填 setter(白名单),
 * @Builder 供纯构造装配位(建行/翻译/入参装配);双构造保无参构造,MP 与存量代码不受影响
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_suggestion")
public class AiSuggestion {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 建议类型:REPLENISH补货/PRICING定价/ANOMALY异常/COPYWRITING文案(封闭词表,随AI服务扩容) */
    private String suggestionType;

    /** 关联店铺ID(shop.id,跨店/全局建议为NULL) */
    private Long shopId;

    /** 关联内部SKU ID(product_sku.id,非SKU维度建议为NULL) */
    private Long skuId;

    /** 关联业务类型(如SHOP_ORDER/INVENTORY,对齐inventory_flow.biz_type风格) */
    private String refType;

    /** 关联业务单据ID */
    private Long refId;

    /** 建议结构化负载(补货量/建议价等,展示与采纳回放用) */
    private String payloadJson;

    /** 建议摘要(列表直显,LLM结论一句话) */
    private String summary;

    /** 风险等级:LOW/MID/HIGH(HIGH须人工复核) */
    private String riskLevel;

    /** 确认状态:0待确认/1已采纳/2已忽略(人工确认后走业务接口,AI禁直接写业务表) */
    private Integer status;

    /** 确认人(sys_user.id) */
    private Long confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
