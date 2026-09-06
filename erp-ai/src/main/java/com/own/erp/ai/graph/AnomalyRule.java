package com.own.erp.ai.graph;

import com.own.erp.ai.constant.AiConsts;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 订单异常检测规则词表(#6 两段式规则半边,2026-09-06 拍板四规则):
 *     规则码→基线风险映射收口本枚举(禁散落字面量,docs/07 §1);V1 不含发货超时逐单版
 *     (AlertEngine 已有同款聚合告警,避免双出口);「同买家批量下单」契约无 buyer 字段(PII 不出契约),
 *     TODO(#6): 随 V2 契约扩容再上。发货超时命中守卫:金额类规则一律要求 paidTime 非空——
 *     Amazon Pending 单落库金额归零(#4 落库口径),无此守卫整批误报
 */
public enum AnomalyRule {

    /** 未支付超时:WAIT_PAY 且下单时间早于 now-N 小时 */
    UNPAID_TIMEOUT("未支付超时", AiConsts.RISK_LOW),

    /** 大额订单:已支付且 orderAmount×exchangeRate ≥ 阈值(本位币口径,汇率缺省按 1) */
    BIG_AMOUNT("大额订单", AiConsts.RISK_MID),

    /** 零元/负数金额:已支付且 orderAmount ≤ 0 */
    ZERO_AMOUNT("零元/负数金额", AiConsts.RISK_HIGH),

    /** 高折扣:已支付且 discountAmount ≥ orderAmount×比率 */
    HIGH_DISCOUNT("高折扣", AiConsts.RISK_MID);

    /** 风险词表序(LOW&lt;MID&lt;HIGH),合并取 max 与 LLM 截断排序共用 */
    private static final Map<String, Integer> RISK_ORDER = Map.of(
            AiConsts.RISK_LOW, 0, AiConsts.RISK_MID, 1, AiConsts.RISK_HIGH, 2);

    private final String label;

    private final String baselineRisk;

    AnomalyRule(String label, String baselineRisk) {
        this.label = label;
        this.baselineRisk = baselineRisk;
    }

    public String getLabel() {
        return label;
    }

    public String getBaselineRisk() {
        return baselineRisk;
    }

    /** 多规则同单命中合并一行,基线风险取最高档 */
    public static String maxRisk(Collection<AnomalyRule> rules) {
        return rules.stream()
                .map(AnomalyRule::getBaselineRisk)
                .max(Comparator.comparingInt(AnomalyRule::riskOrder))
                .orElse(AiConsts.RISK_LOW);
    }

    /** 风险档位序(未知词表按最低档,LLM 回落定级不参与此排序) */
    public static int riskOrder(String risk) {
        return RISK_ORDER.getOrDefault(risk, 0);
    }
}
