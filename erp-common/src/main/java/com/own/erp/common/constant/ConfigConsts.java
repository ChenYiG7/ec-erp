package com.own.erp.common.constant;

import java.util.Set;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : 系统参数词表(#18 系统设置):sys_config 的组/键白名单与边界常量,前后端共用唯一事实源。
 *     键名 = yml relaxed-binding 键同名(erp.ai.system-prompt),DB 无行 = 消费侧走代码默认值;
 *     消费侧写法收口各域 runtime 配置类(erp-ai AiRuntimeProperties 等),禁业务代码散落读键。
 *     凭证类键(openai api-key 等)禁入 sys_config——安全红线 docs/07 §7,凭证只走环境变量/local.properties
 */
public final class ConfigConsts {

    /** 参数组:AI 工作流参数 + AI 对话/Agent 提示词与连接(大模型页签) */
    public static final String GROUP_AI = "AI";

    /** 参数组:库存预警阈值 */
    public static final String GROUP_ALERT = "ALERT";

    /** 参数组:销量日统计 */
    public static final String GROUP_SALES = "SALES";

    /** 合法组词表(保存校验用) */
    public static final Set<String> GROUPS = Set.of(GROUP_AI, GROUP_ALERT, GROUP_SALES);

    /** ── AI 工作流参数键(GROUP_AI)── */

    /** 补货:低库存阈值(库存可用 ≤ 值参与建议),int */
    public static final String KEY_REPLENISH_LOW_STOCK_THRESHOLD = "erp.ai.replenish.low-stock-threshold";

    /** 补货:目标覆盖天数(建议量使库存可支撑 N 天),int */
    public static final String KEY_REPLENISH_COVERAGE_DAYS = "erp.ai.replenish.coverage-days";

    /** 补货:动销统计窗口(天,日均销量分母),int */
    public static final String KEY_REPLENISH_SALES_WINDOW_DAYS = "erp.ai.replenish.sales-window-days";

    /** 补货:最小建议量下限,int */
    public static final String KEY_REPLENISH_MIN_SUGGEST_QTY = "erp.ai.replenish.min-suggest-qty";

    /** 异常检测:大额订单阈值(本位币),decimal 文本 */
    public static final String KEY_ANOMALY_BIG_ORDER_AMOUNT = "erp.ai.anomaly.big-order-amount";

    /** 异常检测:未支付超时(小时),long */
    public static final String KEY_ANOMALY_UNPAID_HOURS = "erp.ai.anomaly.unpaid-hours";

    /** 异常检测:高折扣比率(0~1),decimal 文本 */
    public static final String KEY_ANOMALY_HIGH_DISCOUNT_RATIO = "erp.ai.anomaly.high-discount-ratio";

    /** 异常检测:单轮送 LLM 评分上限(成本护栏),int */
    public static final String KEY_ANOMALY_LLM_MAX_ITEMS = "erp.ai.anomaly.llm-max-items";

    /** 采购建议:单轮送 LLM 摘要的供应商组上限(成本护栏),int */
    public static final String KEY_PURCHASE_LLM_MAX_ITEMS = "erp.ai.purchase.llm-max-items";

    /** 文案生成:单轮送 LLM 生成的商品上限(成本护栏,超限截断下轮再生成),int */
    public static final String KEY_COPY_LLM_MAX_ITEMS = "erp.ai.copy.llm-max-items";

    /** 对话 system prompt(多行文本) */
    public static final String KEY_SYSTEM_PROMPT = "erp.ai.system-prompt";

    /** 补货摘要节点 system prompt(多行文本) */
    public static final String KEY_REPLENISH_SUMMARY_PROMPT = "erp.ai.replenish.summary-prompt";

    /** 异常评分节点 system prompt(多行文本) */
    public static final String KEY_ANOMALY_SCORE_PROMPT = "erp.ai.anomaly.score-prompt";

    /** 采购摘要节点 system prompt(多行文本) */
    public static final String KEY_PURCHASE_SUMMARY_PROMPT = "erp.ai.purchase.summary-prompt";

    /** 文案生成节点 system prompt(多行文本) */
    public static final String KEY_COPY_PROMPT = "erp.ai.copy.prompt";

    /** Agent:客服角色 system prompt(多行文本) */
    public static final String KEY_AGENT_SUPPORT_PROMPT = "erp.ai.agent.support-prompt";

    /** Agent:运营角色 system prompt(多行文本) */
    public static final String KEY_AGENT_OPS_PROMPT = "erp.ai.agent.ops-prompt";

    /** Agent:ReAct 单轮最大思考-行动循环次数(防死循环护栏),int */
    public static final String KEY_AGENT_MAX_ITERS = "erp.ai.agent.max-iters";

    /** Agent:历史重放行数上限(防长会话 token 膨胀),int */
    public static final String KEY_AGENT_HISTORY_MAX_MESSAGES = "erp.ai.agent.history-max-messages";

    /** 工具调用审计行入参 JSON 截断长度,int */
    public static final String KEY_TOOL_AUDIT_MAX_LENGTH = "erp.ai.tool-audit-max-length";

    /** chat 模型名(如 deepseek-chat/glm-4.7,OpenAI 兼容 per-call 覆盖) */
    public static final String KEY_MODEL = "erp.ai.model";

    /** Agent 模型连接三件套:base-url */
    public static final String KEY_AGENT_BASE_URL = "erp.ai.agent.base-url";

    /** Agent 模型连接三件套:model 名 */
    public static final String KEY_AGENT_MODEL = "erp.ai.agent.model";

    /** 知识库检索:命中条数上限(RAG 注入 chat 上下文的 top-k),int */
    public static final String KEY_KB_RETRIEVAL_TOP_K = "erp.ai.kb.retrieval-top-k";

    /** 知识库检索:相似度下限(0~1,低于此分不注入),double */
    public static final String KEY_KB_RETRIEVAL_MIN_SCORE = "erp.ai.kb.retrieval-min-score";

    /** ── AI 组合法词表(保存校验用;基础键组 = GROUP_AI)── */

    /** AI 组合法键全集(api-key 禁入——凭证类键只走环境变量/local.properties,docs/07 §7 安全红线) */
    public static final Set<String> AI_KEYS = Set.of(
            KEY_REPLENISH_LOW_STOCK_THRESHOLD, KEY_REPLENISH_COVERAGE_DAYS,
            KEY_REPLENISH_SALES_WINDOW_DAYS, KEY_REPLENISH_MIN_SUGGEST_QTY,
            KEY_ANOMALY_BIG_ORDER_AMOUNT, KEY_ANOMALY_UNPAID_HOURS,
            KEY_ANOMALY_HIGH_DISCOUNT_RATIO, KEY_ANOMALY_LLM_MAX_ITEMS,
            KEY_PURCHASE_LLM_MAX_ITEMS, KEY_COPY_LLM_MAX_ITEMS,
            KEY_SYSTEM_PROMPT, KEY_REPLENISH_SUMMARY_PROMPT, KEY_ANOMALY_SCORE_PROMPT,
            KEY_PURCHASE_SUMMARY_PROMPT, KEY_COPY_PROMPT,
            KEY_AGENT_SUPPORT_PROMPT, KEY_AGENT_OPS_PROMPT,
            KEY_AGENT_MAX_ITERS, KEY_AGENT_HISTORY_MAX_MESSAGES,
            KEY_TOOL_AUDIT_MAX_LENGTH, KEY_MODEL, KEY_AGENT_BASE_URL, KEY_AGENT_MODEL,
            KEY_KB_RETRIEVAL_TOP_K, KEY_KB_RETRIEVAL_MIN_SCORE);

    /** ── 库存预警键(GROUP_ALERT)── */

    /** 预警:总开关(false 时 AlertJob 直接返回不扫描) */
    public static final String KEY_ALERT_ENABLED = "erp.alert.enabled";

    /** 预警:静默期(小时,同类型告警窗口内只发一条) */
    public static final String KEY_ALERT_QUIET_HOURS = "erp.alert.quiet-hours";

    /** 预警:低库存阈值 */
    public static final String KEY_ALERT_LOW_STOCK_THRESHOLD = "erp.alert.low-stock-threshold";

    /** 预警:发货超时(小时) */
    public static final String KEY_ALERT_SHIP_TIMEOUT_HOURS = "erp.alert.ship-timeout-hours";

    /** 预警:退款异常统计窗口(小时) */
    public static final String KEY_ALERT_REFUND_WINDOW_HOURS = "erp.alert.refund-window-hours";

    /** 预警:退款异常阈值(单店铺窗口内 REFUNDED 单数) */
    public static final String KEY_ALERT_REFUND_COUNT_THRESHOLD = "erp.alert.refund-count-threshold";

    /** 预警:通知内容明细最大条数 */
    public static final String KEY_ALERT_TOP_N = "erp.alert.top-n";

    /** 预警:滞销/积压动销窗口(天) */
    public static final String KEY_ALERT_SLOW_MOVING_DAYS = "erp.alert.slow-moving-days";

    /** 预警:积压阈值(天) */
    public static final String KEY_ALERT_OVERSTOCK_DAYS = "erp.alert.overstock-days";

    /** 预警组合法键全集 */
    public static final Set<String> ALERT_KEYS = Set.of(
            KEY_ALERT_ENABLED, KEY_ALERT_QUIET_HOURS, KEY_ALERT_LOW_STOCK_THRESHOLD,
            KEY_ALERT_SHIP_TIMEOUT_HOURS, KEY_ALERT_REFUND_WINDOW_HOURS,
            KEY_ALERT_REFUND_COUNT_THRESHOLD, KEY_ALERT_TOP_N,
            KEY_ALERT_SLOW_MOVING_DAYS, KEY_ALERT_OVERSTOCK_DAYS);

    /** ── 销量统计键(GROUP_SALES)── */

    /** 销量:总开关(false 时 SalesSnapshotJob 直接返回不重算) */
    public static final String KEY_SALES_ENABLED = "erp.sales.enabled";

    /** 销量:重算窗口(天,含今日) */
    public static final String KEY_SALES_REBUILD_DAYS = "erp.sales.rebuild-days";

    /** 销量组合法键全集 */
    public static final Set<String> SALES_KEYS = Set.of(KEY_SALES_ENABLED, KEY_SALES_REBUILD_DAYS);

    /** 参数值上限(与表列 VARCHAR(1024) 对齐) */
    public static final int VALUE_MAX_LENGTH = 1024;

    private ConfigConsts() {
    }
}
