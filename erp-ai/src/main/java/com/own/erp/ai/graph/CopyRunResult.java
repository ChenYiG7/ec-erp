package com.own.erp.ai.graph;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 文案生成工作流运行摘要(#17,同 PurchaseRunResult 口径,手动触发返回体)
 */
@Builder
public record CopyRunResult(

        /** 本轮扫描启用商品总数 */
        int scannedCount,

        /** 去重跳过数(同商品已有待确认文案建议) */
        int pendingSkippedCount,

        /** 成功产出并落库建议数 */
        int generatedCount,

        /** 是否发生降级(LLM 不可用/调用失败/解析失败/逐商品漏回跳过) */
        boolean degraded
) {
}
