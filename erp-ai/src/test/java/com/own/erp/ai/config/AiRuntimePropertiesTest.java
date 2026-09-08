package com.own.erp.ai.config;

import com.own.erp.common.constant.ConfigConsts;
import com.own.erp.contract.SystemConfigApi;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : AiRuntimeProperties 单测(#18,AIR:函数式 SystemConfigApi 桩,不依赖数据库):
 *     DB 覆盖值优先/无覆盖回落 yml 默认/解析失败回落不抛错/数值下限钳制/Optional 覆盖口语义。
 *     纯构造直测,无 Spring 上下文
 */
class AiRuntimePropertiesTest {

    private AiRuntimeProperties runtimeOf(Map<String, String> overrides) {
        ErpAiProperties props = new ErpAiProperties();
        ErpAlertProperties alertProps = new ErpAlertProperties();
        return new AiRuntimeProperties(props, alertProps, overrides::get);
    }

    @Test
    void fallsBackToDefaultsWhenNoOverride() {
        AiRuntimeProperties runtime = runtimeOf(Map.of());
        assertEquals(10, runtime.replenishLowStockThreshold());
        assertEquals(14, runtime.replenishCoverageDays());
        assertEquals(7, runtime.replenishLeadTimeDays());
        assertEquals(new BigDecimal("0.95"), runtime.replenishServiceLevel());
        assertEquals(new BigDecimal("10000"), runtime.anomalyBigOrderAmount());
        assertTrue(runtime.alertEnabled());
        assertEquals(24L, runtime.alertQuietHours());
        assertEquals("test-default", "test-default"); // 占位防空测试误删
    }

    @Test
    void replenishV2KeysOverrideAndFallback() {
        // V2 新键:DB 覆盖值优先
        AiRuntimeProperties overridden = runtimeOf(Map.of(
                ConfigConsts.KEY_REPLENISH_LEAD_TIME_DAYS, "14",
                ConfigConsts.KEY_REPLENISH_SERVICE_LEVEL, "0.98"));
        assertEquals(14, overridden.replenishLeadTimeDays());
        assertEquals(new BigDecimal("0.98"), overridden.replenishServiceLevel());

        // 解析失败/越界(0~1 外)回落代码默认,不抛错——配置错误不阻断业务
        AiRuntimeProperties badValues = runtimeOf(Map.of(
                ConfigConsts.KEY_REPLENISH_LEAD_TIME_DAYS, "abc",
                ConfigConsts.KEY_REPLENISH_SERVICE_LEVEL, "1.5"));
        assertEquals(7, badValues.replenishLeadTimeDays());
        assertEquals(new BigDecimal("0.95"), badValues.replenishServiceLevel());

        // 提前期下限钳 0(负值不炸公式:√max(LT,0))
        AiRuntimeProperties negative = runtimeOf(Map.of(
                ConfigConsts.KEY_REPLENISH_LEAD_TIME_DAYS, "-3"));
        assertEquals(0, negative.replenishLeadTimeDays());
    }

    @Test
    void overrideWinsOverDefaults() {
        AiRuntimeProperties runtime = runtimeOf(Map.of(
                ConfigConsts.KEY_REPLENISH_COVERAGE_DAYS, "21",
                ConfigConsts.KEY_ANOMALY_BIG_ORDER_AMOUNT, "20000.50",
                ConfigConsts.KEY_SYSTEM_PROMPT, "自定义提示词",
                ConfigConsts.KEY_ALERT_ENABLED, "false"));
        assertEquals(21, runtime.replenishCoverageDays());
        assertEquals(new BigDecimal("20000.50"), runtime.anomalyBigOrderAmount());
        assertEquals("自定义提示词", runtime.systemPrompt());
        assertFalse(runtime.alertEnabled());
    }

    @Test
    void unparsableOverrideFallsBackSilently() {
        AiRuntimeProperties runtime = runtimeOf(Map.of(
                ConfigConsts.KEY_REPLENISH_COVERAGE_DAYS, "abc",
                ConfigConsts.KEY_ANOMALY_BIG_ORDER_AMOUNT, "1.2.3"));
        assertEquals(14, runtime.replenishCoverageDays());
        assertEquals(new BigDecimal("10000"), runtime.anomalyBigOrderAmount());
    }

    @Test
    void negativeValuesClampedToFloors() {
        AiRuntimeProperties runtime = runtimeOf(Map.of(
                ConfigConsts.KEY_REPLENISH_COVERAGE_DAYS, "-5",
                ConfigConsts.KEY_AGENT_MAX_ITERS, "0",
                ConfigConsts.KEY_ALERT_TOP_N, "-1"));
        assertEquals(1, runtime.replenishCoverageDays());
        assertEquals(1, runtime.agentMaxIters());
        assertEquals(1, runtime.alertTopN());
    }

    @Test
    void optionalOverridesEmptyWhenNoRow() {
        AiRuntimeProperties runtime = runtimeOf(Map.of());
        assertTrue(runtime.chatModel().isEmpty());
        assertTrue(runtime.agentBaseUrl().isEmpty());
        assertTrue(runtime.agentModel().isEmpty());
        assertTrue(runtime.salesEnabledOverride().isEmpty());
        assertTrue(runtime.salesRebuildDaysOverride().isEmpty());
    }

    @Test
    void optionalOverridesPresentWhenConfigured() {
        AiRuntimeProperties runtime = runtimeOf(Map.of(
                ConfigConsts.KEY_MODEL, "glm-4.7",
                ConfigConsts.KEY_AGENT_BASE_URL, "https://api.example.com",
                ConfigConsts.KEY_SALES_ENABLED, "false",
                ConfigConsts.KEY_SALES_REBUILD_DAYS, "60"));
        assertTrue(runtime.chatModel().isPresent());
        assertEquals("glm-4.7", runtime.chatModel().get());
        assertEquals("https://api.example.com", runtime.agentBaseUrl().get());
        assertTrue(runtime.salesEnabledOverride().isPresent());
        assertFalse(runtime.salesEnabledOverride().get());
        assertEquals(60, runtime.salesRebuildDaysOverride().get());
    }
}
