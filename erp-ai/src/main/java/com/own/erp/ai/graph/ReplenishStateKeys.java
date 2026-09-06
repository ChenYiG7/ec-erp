package com.own.erp.ai.graph;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 补货工作流 OverAllState 状态键(#6 SAA Graph):节点间传递契约收口本类,
 *     禁散落字符串字面量(docs/07 §1 禁魔法值);各键合并策略均为 REPLACE(见 ReplenishWorkflow 装配)
 */
public final class ReplenishStateKeys {

    /** 扫描到的低库存 SKU 聚合行(List&lt;ReplenishItem&gt;) */
    public static final String KEY_ITEMS = "items";

    /** 本轮扫描的库存行数(int) */
    public static final String KEY_SCANNED = "scanned";

    /** 摘要是否降级(boolean:true=LLM 不可用走了模板) */
    public static final String KEY_DEGRADED = "degraded";

    /** 已落库建议条数(int) */
    public static final String KEY_PERSISTED = "persisted";

    private ReplenishStateKeys() {
    }
}
