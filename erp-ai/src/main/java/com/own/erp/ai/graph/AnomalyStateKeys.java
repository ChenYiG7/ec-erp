package com.own.erp.ai.graph;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 订单异常检测工作流 OverAllState 状态键(#6 SAA Graph):节点间传递契约收口本类,
 *     禁散落字符串字面量(docs/07 §1);各键合并策略均为 REPLACE(见 AnomalyWorkflow 装配)
 */
public final class AnomalyStateKeys {

    /** 可疑订单聚合行(List&lt;AnomalyItem&gt;) */
    public static final String KEY_ITEMS = "items";

    /** 本轮扫描的订单行数(int,两状态合计) */
    public static final String KEY_SCANNED = "scanned";

    /** LLM 评分是否降级(boolean:true=评分走了规则回落;超限未送评不算降级) */
    public static final String KEY_DEGRADED = "degraded";

    /** 已落库建议条数(int) */
    public static final String KEY_PERSISTED = "persisted";

    /** 已送 LLM 评分且采纳其定级的单数(int) */
    public static final String KEY_LLM_SCORED = "llmScored";

    private AnomalyStateKeys() {
    }
}
