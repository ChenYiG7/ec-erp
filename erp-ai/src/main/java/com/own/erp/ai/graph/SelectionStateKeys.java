package com.own.erp.ai.graph;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 智能选品工作流 OverAllState 状态键(#17 落位表「智能选品」三期提前,同 PurchaseStateKeys 纪律):
 *     节点间传递契约收口本类,禁散落字符串字面量(docs/07 §1 禁魔法值);
 *     各键合并策略均为 REPLACE(见 SelectionWorkflow 装配)
 */
public final class SelectionStateKeys {

    /** 评分候选行(List&lt;SelectionCandidate&gt;,collect 节点产出,含全部数据缺口标记) */
    public static final String KEY_CANDIDATES = "candidates";

    /** 综合评分入选行(List&lt;SelectionCandidate&gt;,score 节点产出,top persistMaxItems) */
    public static final String KEY_SELECTED = "selected";

    /** 本轮扫描的库存行数(int) */
    public static final String KEY_SCANNED = "scanned";

    /** 摘要是否降级(boolean:true=LLM 不可用走了模板) */
    public static final String KEY_DEGRADED = "degraded";

    /** 已落库建议条数(int) */
    public static final String KEY_PERSISTED = "persisted";

    private SelectionStateKeys() {
    }
}
