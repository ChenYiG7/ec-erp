package com.own.erp.ai.graph;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 补货建议工作流的 SKU 聚合行(#6 SAA Graph):跨仓合并口径(ai_suggestion 无仓库列),
 *     同 skuId 的可用/在途多仓求和;collect 节点产原始行(suggestQty=0),calculate 节点回填建议量,
 *     summarize 节点回填自然语言摘要(persist 落 ai_suggestion)
 */
@Builder(toBuilder = true)
public record ReplenishItem(

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** 可用合计(跨仓求和) */
        int qtyAvailable,

        /** 在途合计(跨仓求和,采购在途抵扣建议量) */
        int qtyTransit,

        /** 建议补货量(calculate 节点回填) */
        int suggestQty,

        /** 摘要(summarize 节点回填:LLM 文案或降级模板) */
        String summary
) {
}
