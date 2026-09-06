package com.own.erp.ai.graph;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 补货工作流运行摘要(#6 SAA Graph):run() 出参,Controller 直接透传;
 *     degraded=true 表示 LLM 摘要走了模板降级(建议本身照常产出)
 */
@Builder
public record ReplenishRunResult(

        /** 扫描库存行数 */
        int scannedCount,

        /** 产出建议的 SKU 数 */
        int suggestedCount,

        /** 落库 ai_suggestion 条数 */
        int persistedCount,

        /** 摘要是否降级(LLM 不可用走模板) */
        boolean degraded
) {
}
