package com.own.erp.ai.graph;

import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 文案生成工作流行模型(#17 三期候选「产品描述生成」V1):
 *     collect 段装配材料(商品名/品牌/类目/SPU 销售属性/SKU 规格行),generate 段回填 LLM 产出
 *     (标题/五点描述/商品描述/搜索关键词)——record+@Builder(toBuilder) 同 AnomalyItem 口径,
 *     材料字段只读、产出字段经 toBuilder 回填。prompt 材料不含成本价/申报价值等内部价格字段
     *     (价格不进文案 prompt,防模型抄成本价当售价,docs/07 §7 数据最小化口径)
 */
@Builder(toBuilder = true)
public record CopyItem(

        /** 商品ID(product.id,建议 refId 与 LLM 对齐键) */
        Long productId,

        /** 内部SPU编码(材料,prompt 不带——内部编码对模型无意义防编造) */
        String spuCode,

        /** 商品名称(标题生成核心材料) */
        String productName,

        /** 品牌名称(可空,标题常规要素) */
        String brandName,

        /** 类目名称(可空,类目词进关键词) */
        String categoryName,

        /** SPU 销售属性(原样 JSON 串,可空) */
        String attrsJson,

        /** SKU 规格行(截前 {@link CopyCollectNode#SKU_PROMPT_MAX} 条防 prompt 拉爆) */
        List<SkuLine> skus,

        /** LLM 产出:listing 标题(采纳建议 summary 即标题) */
        String title,

        /** LLM 产出:五点描述(可空列表) */
        List<String> bulletPoints,

        /** LLM 产出:商品描述 */
        String description,

        /** LLM 产出:搜索关键词(可空列表) */
        List<String> keywords
) {

    /** SKU 规格行(prompt 紧凑序列化用,只带文案相关字段——成本价/条码/HS 编码不进 prompt) */
    @Builder
    public record SkuLine(

            /** 内部 SKU 编码 */
            String skuCode,

            /** 规格值(原样 JSON 串,可空) */
            String attrsJson,

            /** 重量(克,可空,物流卖点材料) */
            Integer weightG,

            /** 含电标记 1/0(跨境合规卖点材料) */
            Integer battery
    ) {
    }
}
