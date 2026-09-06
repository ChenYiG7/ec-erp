package com.own.erp.ai.graph;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 订单异常检测工作流运行摘要(#6 SAA Graph):run() 出参,Controller 直接透传;
 *     degraded=true 表示 LLM 评分走了规则回落(建议本身照常产出,llmScoredCount 只计采纳 LLM 定级的单)
 */
@Builder
public record AnomalyRunResult(

        /** 扫描订单行数(WAIT_PAY/WAIT_SHIP 两态合计) */
        int scannedCount,

        /** 命中规则的可疑单数 */
        int suspiciousCount,

        /** 落库 ai_suggestion 条数 */
        int persistedCount,

        /** 采纳 LLM 定级的单数(降级/超限未送评不计) */
        int llmScoredCount,

        /** 评分是否降级(LLM 不可用/失败/漏回落) */
        boolean degraded
) {
}
