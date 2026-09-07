package com.own.erp.ai.config;

import cn.hutool.core.util.StrUtil;
import com.own.erp.ai.config.ErpAiProperties;
import com.own.erp.contract.SystemConfigApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AI 运行时配置收口(#18 系统设置,2026-09-07 拍板"大模型等启动后可变项前端可配"):
 *     优先级 = sys_config DB 覆盖值 > ErpAiProperties(yml/代码默认);DB 值由 SystemConfigApi 读取
 *     (实现收口 erp-api,30s TTL 缓存,保存端点即时失效),解析失败/越界回落代码默认值不抛错——
 *     配置错误不阻断业务(与"无 key 降级不炸"同口径)。
 *     取值方法均即时调用(Job 每轮取一次,chat/agent 每请求取一次),禁 @PostConstruct 快照——快照即失去热更语义。
 *     数值下限钳制在取值口统一收口(0 钳制/下限 1),消费侧不重复写护栏;
 *     模型连接三件套(base-url/api-key/model)只出 agent 侧(chat 侧模型名单独走 KEY_MODEL per-call 覆盖);
 *     api-key 无 DB 键(凭证禁入 sys_config,docs/07 §7),只透传 yml 值。
 *     预警默认值取自 ErpAlertProperties(同模块);销量默认值在 ErpSalesProperties(erp-api),
 *     erp-ai 禁反向依赖(铁律 2),销量两键出 Optional 覆盖口由 SalesSnapshotJob 自行回落
 */
@Component
@Slf4j
public class AiRuntimeProperties {

    private final ErpAiProperties props;
    private final ErpAlertProperties alertProps;
    private final SystemConfigApi configApi;

    /** @Lazy 断构造环(docs/07 §2.2):SystemConfigApi 实现在 erp-api,依赖 erp-system */
    @Autowired
    public AiRuntimeProperties(ErpAiProperties props, ErpAlertProperties alertProps,
                               @Lazy SystemConfigApi configApi) {
        this.props = props;
        this.alertProps = alertProps;
        this.configApi = configApi;
    }

    /** 取覆盖值并解析;无覆盖/解析失败返回 empty(回落代码默认) */
    private <T> Optional<T> override(String key, Function<String, T> parser) {
        String raw = configApi.valueOf(key);
        if (StrUtil.isBlank(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.apply(raw.trim()));
        } catch (Exception e) {
            log.warn("系统参数 {} 覆盖值解析失败({}),回落代码默认值", key, e.getMessage());
            return Optional.empty();
        }
    }
    private int intOf(String key, int defaultValue, int min) {
        Integer parsed = override(key, Integer::parseInt).orElse(null);
        int value = parsed == null ? defaultValue : parsed;
        return Math.max(value, min);
    }

    private long longOf(String key, long defaultValue, long min) {
        Long parsed = override(key, Long::parseLong).orElse(null);
        return parsed == null ? defaultValue : Math.max(parsed, min);
    }

    private BigDecimal decimalOf(String key, BigDecimal defaultValue) {
        return override(key, BigDecimal::new).orElse(defaultValue);
    }

    private String textOf(String key, String defaultValue) {
        return override(key, Function.identity()).orElse(defaultValue);
    }

    private boolean boolOf(String key, boolean defaultValue) {
        return override(key, Boolean::parseBoolean).orElse(defaultValue);
    }

    /** ── 补货工作流参数 ── */

    /** 低库存阈值(int,≥0) */
    public int replenishLowStockThreshold() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_REPLENISH_LOW_STOCK_THRESHOLD,
                props.getReplenish().getLowStockThreshold(), 0);
    }

    /** 目标覆盖天数(int,≥1) */
    public int replenishCoverageDays() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_REPLENISH_COVERAGE_DAYS,
                props.getReplenish().getCoverageDays(), 1);
    }

    /** 动销统计窗口天数(int,≥1) */
    public int replenishSalesWindowDays() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_REPLENISH_SALES_WINDOW_DAYS,
                props.getReplenish().getSalesWindowDays(), 1);
    }

    /** 最小建议量下限(int,≥0) */
    public int replenishMinSuggestQty() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_REPLENISH_MIN_SUGGEST_QTY,
                props.getReplenish().getMinSuggestQty(), 0);
    }

    /** 摘要节点 system prompt */
    public String replenishSummaryPrompt() {
        return textOf(com.own.erp.common.constant.ConfigConsts.KEY_REPLENISH_SUMMARY_PROMPT,
                props.getReplenish().getSummaryPrompt());
    }

    /** ── 订单异常检测参数 ── */

    /** 大额订单阈值(本位币,decimal) */
    public BigDecimal anomalyBigOrderAmount() {
        return decimalOf(com.own.erp.common.constant.ConfigConsts.KEY_ANOMALY_BIG_ORDER_AMOUNT,
                props.getAnomaly().getBigOrderAmount());
    }

    /** 未支付超时(小时,long,≥0) */
    public long anomalyUnpaidHours() {
        return longOf(com.own.erp.common.constant.ConfigConsts.KEY_ANOMALY_UNPAID_HOURS,
                props.getAnomaly().getUnpaidHours(), 0);
    }

    /** 高折扣比率(0~1,decimal) */
    public BigDecimal anomalyHighDiscountRatio() {
        return decimalOf(com.own.erp.common.constant.ConfigConsts.KEY_ANOMALY_HIGH_DISCOUNT_RATIO,
                props.getAnomaly().getHighDiscountRatio());
    }

    /** 单轮送 LLM 评分上限(int,≥0) */
    public int anomalyLlmMaxItems() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_ANOMALY_LLM_MAX_ITEMS,
                props.getAnomaly().getLlmMaxItems(), 0);
    }

    /** 评分节点 system prompt */
    public String anomalyScorePrompt() {
        return textOf(com.own.erp.common.constant.ConfigConsts.KEY_ANOMALY_SCORE_PROMPT,
                props.getAnomaly().getScorePrompt());
    }

    /** ── 对话/Agent 提示词与护栏 ── */

    /** 对话 system prompt */
    public String systemPrompt() {
        return textOf(com.own.erp.common.constant.ConfigConsts.KEY_SYSTEM_PROMPT,
                props.getSystemPrompt());
    }

    /** Agent 客服角色 system prompt */
    public String agentSupportPrompt() {
        return textOf(com.own.erp.common.constant.ConfigConsts.KEY_AGENT_SUPPORT_PROMPT,
                props.getAgent().getSupportPrompt());
    }

    /** Agent 运营角色 system prompt */
    public String agentOpsPrompt() {
        return textOf(com.own.erp.common.constant.ConfigConsts.KEY_AGENT_OPS_PROMPT,
                props.getAgent().getOpsPrompt());
    }

    /** Agent ReAct 最大循环次数(int,≥1) */
    public int agentMaxIters() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_AGENT_MAX_ITERS,
                props.getAgent().getMaxIters(), 1);
    }

    /** Agent 历史重放行数上限(int,≥1) */
    public int agentHistoryMaxMessages() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_AGENT_HISTORY_MAX_MESSAGES,
                props.getAgent().getHistoryMaxMessages(), 1);
    }

    /** 工具审计行入参截断长度(int,≥1) */
    public int toolAuditMaxLength() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_TOOL_AUDIT_MAX_LENGTH,
                props.getToolAuditMaxLength(), 1);
    }

    /** ── 模型连接(chat per-call 模型名 + agent 连接三件套)── */

    /** chat 模型名覆盖;无覆盖返回 empty(chat 侧按需挂 per-call options) */
    public Optional<String> chatModel() {
        return override(com.own.erp.common.constant.ConfigConsts.KEY_MODEL, Function.identity());
    }

    /** Agent 模型 base-url 覆盖;无覆盖返回 empty */
    public Optional<String> agentBaseUrl() {
        return override(com.own.erp.common.constant.ConfigConsts.KEY_AGENT_BASE_URL, Function.identity());
    }

    /** Agent 模型名覆盖;无覆盖返回 empty */
    public Optional<String> agentModel() {
        return override(com.own.erp.common.constant.ConfigConsts.KEY_AGENT_MODEL, Function.identity());
    }

    /** ── 库存预警(⑥:AlertEngine/AlertJob 取值切 Provider;护栏扫描量不入表)── */

    /** 预警总开关(false 时 AlertJob 直接返回不扫描) */
    public boolean alertEnabled() {
        return boolOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_ENABLED, alertProps.isEnabled());
    }

    /** 静默期(小时,long,≥0) */
    public long alertQuietHours() {
        return longOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_QUIET_HOURS,
                alertProps.getQuietHours(), 0);
    }

    /** 低库存阈值(int,≥0) */
    public int alertLowStockThreshold() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_LOW_STOCK_THRESHOLD,
                alertProps.getLowStockThreshold(), 0);
    }

    /** 发货超时(小时,long,≥0) */
    public long alertShipTimeoutHours() {
        return longOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_SHIP_TIMEOUT_HOURS,
                alertProps.getShipTimeoutHours(), 0);
    }

    /** 退款异常统计窗口(小时,long,≥0) */
    public long alertRefundWindowHours() {
        return longOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_REFUND_WINDOW_HOURS,
                alertProps.getRefundWindowHours(), 0);
    }

    /** 退款异常阈值(int,≥0) */
    public int alertRefundCountThreshold() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_REFUND_COUNT_THRESHOLD,
                alertProps.getRefundCountThreshold(), 0);
    }

    /** 通知明细最大条数(int,≥1) */
    public int alertTopN() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_TOP_N, alertProps.getTopN(), 1);
    }

    /** 滞销/积压动销窗口(天,int,≥1) */
    public int alertSlowMovingDays() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_SLOW_MOVING_DAYS,
                alertProps.getSlowMovingDays(), 1);
    }

    /** 积压阈值(天,int,≥1) */
    public int alertOverstockDays() {
        return intOf(com.own.erp.common.constant.ConfigConsts.KEY_ALERT_OVERSTOCK_DAYS,
                alertProps.getOverstockDays(), 1);
    }

    /**
     * 销量统计总开关:ErpSalesProperties 在 erp-api(erp-ai 禁反向依赖,铁律 2),
     * 此处无法引用其默认值——DB 无行返回 empty,由 SalesSnapshotJob(erp-api)自行回落 yml 默认
     */
    public Optional<Boolean> salesEnabledOverride() {
        return override(com.own.erp.common.constant.ConfigConsts.KEY_SALES_ENABLED, Boolean::parseBoolean);
    }

    /** 销量重算窗口覆盖(天);DB 无行返回 empty,由 SalesSnapshotJob 自行回落 yml 默认 */
    public Optional<Integer> salesRebuildDaysOverride() {
        return override(com.own.erp.common.constant.ConfigConsts.KEY_SALES_REBUILD_DAYS, Integer::parseInt);
    }
}
