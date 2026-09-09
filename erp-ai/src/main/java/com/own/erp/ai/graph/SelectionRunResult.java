package com.own.erp.ai.graph;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 智能选品工作流运行摘要(#17):run() 出参,Controller 直接透传;
 *     degraded=true 表示 LLM 摘要走了模板降级(建议本身照常产出,评分为确定性程序结果不受影响)
 */
@Builder
public record SelectionRunResult(

        /** 扫描库存行数 */
        int scannedCount,

        /** 评分候选 SKU 数(启用商品 ∩ 有库存行,去重后) */
        int candidateCount,

        /** 综合评分入选数(top persistMaxItems,落库前) */
        int selectedCount,

        /** 落库 ai_suggestion 条数 */
        int persistedCount,

        /** 摘要是否降级(LLM 不可用走模板) */
        boolean degraded
) {
}
