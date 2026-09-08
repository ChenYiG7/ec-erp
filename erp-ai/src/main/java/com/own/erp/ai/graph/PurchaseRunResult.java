package com.own.erp.ai.graph;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 采购建议工作流运行摘要(#17):run() 出参,Controller 直接透传;
 *     degraded=true 表示 LLM 摘要走了模板降级(建议本身照常产出)
 */
@Builder
public record PurchaseRunResult(

        /** 扫描库存行数 */
        int scannedCount,

        /** 计算出补货建议量的 SKU 数(聚合输入) */
        int suggestedSkuCount,

        /** 无法定位供应商而未纳入的 SKU 数(无采购历史) */
        int noSupplierCount,

        /** 聚合出的供应商组数(落库前,含去重跳过) */
        int groupCount,

        /** 落库 ai_suggestion 条数 */
        int persistedCount,

        /** 摘要是否降级(LLM 不可用走模板) */
        boolean degraded
) {
}
