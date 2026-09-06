package com.own.erp.ai.config;

import cn.hutool.core.util.StrUtil;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AI 提示词集中配置(docs/07 §9:提示词集中配置类,禁散落业务代码字符串):
 *         system prompt 默认值在此维护,yml `erp.ai.system-prompt` 可整体覆盖;模型连接
 *         (base-url/api-key/model)沿用 spring.ai.openai.* 官方配置段,不重复建键
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "erp.ai")
public class ErpAiProperties {

    /** 对话 system prompt:只读助手约束(工具白名单/禁编造/禁写操作引导/中文简洁回答) */
    private String systemPrompt = """
            你是电商 ERP 智能助手。规则:
            1. 只能通过提供的只读工具查询数据回答问题,禁止编造或估算数据;查不到就如实说明。
            2. 你没有任何写操作能力:库存调整、改价、发货、售后处理等必须提示用户在系统页面人工操作。
            3. 金额均为原币金额,注意说明币种;不要自行换算汇率。
            4. 回答用中文,先给结论再给依据;列表类回答不超过 20 条,数据多时提示用户细化过滤条件。""";

    /** 工具调用审计行(TOOL 中间行)入参 JSON 截断长度,防超长入参撑爆审计表 */
    private int toolAuditMaxLength = 500;

    /** 补货建议工作流参数(#6 SAA Graph):公式/扫描护栏全走配置不硬编码 */
    private Replenish replenish = new Replenish();

    /** 订单异常检测参数组(#6 两段式:规则先筛+LLM 只评可疑样本):阈值/护栏/prompt 全走配置不硬编码 */
    private Anomaly anomaly = new Anomaly();

    /**
     * 补货工作流参数组:V1 无销量统计面,日均销量用固定估计值(assumedDailySales),
     * TODO(#6): 销量数据面落地后按近期动销重估;扫描护栏语义同 erp.alert
     */
    @Getter
    @Setter
    public static class Replenish {

        /** 定时开关(erp-api ReplenishJob;cron 走 erp.ai.replenish.cron 占位符,不在此重复建键) */
        private boolean enabled = true;

        /** 低库存阈值:inventory.qty_available ≤ 此值参与补货建议 */
        private int lowStockThreshold = 10;

        /** 目标覆盖天数:建议量使库存可支撑 N 天 */
        private int coverageDays = 14;

        /** 动销统计窗口(天,含今日,#6 2026-09-07 真实动销落地):日均销量 = 窗口内销量合计/窗口天数
         *  (SalesQueryApi 读 order_sales_daily);窗口内零动销的 SKU 不再硬补(死 SKU 免每日建议) */
        private int salesWindowDays = 30;

        /** 最小建议量下限(仅对有动销的 SKU 起下限作用,建议量低于此值按此值) */
        private int minSuggestQty = 10;

        /** 单页扫描量(契约钳制 ≤100) */
        private int scanPageSize = 100;

        /** 单轮扫描行数上限 */
        private int scanMaxRows = 500;

        /** 摘要节点 system prompt(集中配置,docs/07 §9) */
        private String summaryPrompt = "你是电商 ERP 的补货分析助手。根据给定的库存与建议补货量,为每个 SKU 写一句简短中文摘要,"
                + "说明补货理由(如缺货风险/覆盖天数)。只输出 JSON 数组,不输出任何其他文字。";
    }

    /**
     * 订单异常检测参数组(#6 两段式,2026-09-06 拍板四规则:未付超时/大额/0元负数/高折扣):
     * 扫描口径只扫 WAIT_PAY/WAIT_SHIP 两态(待处理可干预,终态历史单不扫防重复命中);
     * 大额阈值为本位币口径(orderAmount×exchangeRate,汇率缺省按 1,#4 落库口径);
     * 金额类规则一律要求 paidTime 非空——Amazon Pending 单落库金额归零,无此守卫整批误报
     */
    @Getter
    @Setter
    public static class Anomaly {

        /** 定时开关(erp-api AnomalyJob;cron 走 erp.ai.anomaly.cron 占位符,不在此重复建键) */
        private boolean enabled = true;

        /** 大额订单阈值(本位币,orderAmount×exchangeRate ≥ 此值命中) */
        private BigDecimal bigOrderAmount = new BigDecimal("10000");

        /** 未支付超时(小时):WAIT_PAY 且下单时间早于 now-N 小时命中 */
        private long unpaidHours = 48;

        /** 高折扣比率:已支付且 discountAmount ≥ orderAmount×此比率命中 */
        private BigDecimal highDiscountRatio = new BigDecimal("0.5");

        /** 单页扫描量(契约钳制 ≤100) */
        private int scanPageSize = 100;

        /** 单状态单轮扫描行数上限(分页扫全量的兜底护栏,防大表拖死) */
        private int scanMaxRows = 1000;

        /** 单轮送 LLM 评分的可疑单上限(超限按基线风险降序截断,未送评单直接规则定级) */
        private int llmMaxItems = 20;

        /** 评分节点 system prompt(集中配置,docs/07 §9) */
        private String scorePrompt = "你是电商 ERP 的订单风控助手。根据给定的订单信息与其命中的规则,为每张可疑订单评估风险等级"
                + "(只能取 LOW/MID/HIGH 之一)并给一句不超过 40 字的中文理由。只输出 JSON 数组,"
                + "元素形如 {\"orderId\":1,\"riskLevel\":\"MID\",\"reason\":\"...\"},不输出任何其他文字。";
    }
}
