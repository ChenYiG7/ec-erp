package com.own.erp.ai.graph;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 文案生成工作流状态键(#17 三期候选「产品描述生成」V1,SAA Graph OverAllState 键,
 *     全 REPLACE 策略,同 Replenish/Anomaly/Purchase 三工作流母本口径)
 */
public final class CopyStateKeys {

    /** 待生成文案的商品材料行(collect 产出 → generate 逐商品对齐后仅保留成功行) */
    public static final String KEY_ITEMS = "copyItems";

    /** 本轮扫描商品总数 */
    public static final String KEY_SCANNED = "copyScanned";

    /** 去重跳过数(同商品已有待确认文案建议) */
    public static final String KEY_SKIPPED = "copySkipped";

    /** 是否发生降级(LLM 不可用/调用失败/解析失败/逐商品漏回或字段缺失被跳过) */
    public static final String KEY_DEGRADED = "copyDegraded";

    /** 成功落库建议数 */
    public static final String KEY_PERSISTED = "copyPersisted";

    private CopyStateKeys() {
    }
}
