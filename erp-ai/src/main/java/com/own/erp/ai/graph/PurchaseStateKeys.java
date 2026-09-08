package com.own.erp.ai.graph;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 采购建议工作流 OverAllState 状态键(#17 三期候选,同 ReplenishStateKeys 纪律):
 *     节点间传递契约收口本类,禁散落字符串字面量(docs/07 §1 禁魔法值);
 *     各键合并策略均为 REPLACE(见 PurchaseWorkflow 装配)
 */
public final class PurchaseStateKeys {

    /** 补货计算后的 SKU 建议行(List&lt;ReplenishItem&gt;,collect 节点产出,复用补货行模型) */
    public static final String KEY_ITEMS = "items";

    /** 供应商聚合组(List&lt;PurchaseGroup&gt;,aggregate 节点产出) */
    public static final String KEY_GROUPS = "groups";

    /** 无法定位供应商的 SKU 数(int,无采购历史,未纳入建议) */
    public static final String KEY_NO_SUPPLIER = "noSupplier";

    /** 本轮扫描的库存行数(int) */
    public static final String KEY_SCANNED = "scanned";

    /** 摘要是否降级(boolean:true=LLM 不可用走了模板) */
    public static final String KEY_DEGRADED = "degraded";

    /** 已落库建议条数(int) */
    public static final String KEY_PERSISTED = "persisted";

    private PurchaseStateKeys() {
    }
}
