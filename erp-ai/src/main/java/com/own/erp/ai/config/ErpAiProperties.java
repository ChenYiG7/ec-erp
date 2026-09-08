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

    /** 采购建议工作流参数组(#17 三期候选落地,2026-09-08):输入复用补货计算口径
     *  (低库存阈值/覆盖天数/动销窗口/最小建议量四参数走 replenish 同源键,语义=同一"低库存"定义),
     *  仅扫描护栏与摘要配置独立;V1 仅手动触发(采购是人类决策节奏),定时接线待拍板 */
    private Purchase purchase = new Purchase();

    /** 文案生成工作流参数组(#17 三期候选「产品描述生成」落地,2026-09-08):
     *  扫描护栏独立;LLM 上限与 prompt 入 sys_config(#18 热更);V1 仅手动触发不接定时 */
    private Copy copy = new Copy();

    /** Agent 参数组(四期 agent/):角色 system prompt 收口本类(docs/07 §9),yml `erp.ai.agent.*` 可覆盖 */
    private Agent agent = new Agent();

    /** 知识库参数组(RAG V1,2026-09-08):切块/索引文件/上传护栏 yml 口径;检索 top-k 与相似度下限入 sys_config(#18 热更) */
    private Kb kb = new Kb();

    /**
     * 知识库参数组(#6 AI 客服 RAG V1):向量库拍板 = Spring AI SimpleVectorStore(JSON 文件持久化,
     * 零新基建;VectorStore 接口编程,后续换 pgvector/Redis 只换实现);向量不入库——
     * ai_kb_chunk 存 chunk 文本作为重建正本,索引文件丢失/换 embedding 模型时按正本重建
     */
    @Getter
    @Setter
    public static class Kb {

        /** 向量索引文件路径(SimpleVectorStore JSON 持久化;相对路径相对进程工作目录) */
        private String indexPath = "data/ai/kb-index.json";

        /** 切块目标 token 数(TokenTextSplitter chunkSize;中文 ≈ 等量字符级,检索粒度调参面) */
        private int chunkSize = 800;

        /** 上传文件大小上限(字节;防超长文件拉爆切块与 embedding) */
        private int maxFileBytes = 1048576;

        /** 单文档最大字符数(文件解码后/粘贴文本同口径,超限拒绝) */
        private int maxDocumentChars = 200000;

        /** 检索条数默认值(sys_config erp.ai.kb.retrieval-top-k 可覆盖) */
        private int retrievalTopK = 4;

        /** 检索相似度下限默认值(0~1,sys_config erp.ai.kb.retrieval-min-score 可覆盖) */
        private double retrievalMinScore = 0.5;
    }

    /**
     * Agent 参数组:ReActAgent 角色 prompt 与循环护栏。
     * 提示词纪律同 chat:只读工具查数据/禁编造/写操作引导人工页面;模型连接复用 spring.ai.openai.*(不重复建键)
     */
    @Getter
    @Setter
    public static class Agent {

        /** 客服 Agent system prompt(全量只读工具:订单/库存/商品/售后) */
        private String supportPrompt = """
                你是电商 ERP 智能客服助手。规则:
                1. 只能通过提供的只读工具查询数据回答问题,禁止编造或估算数据;查不到就如实说明。
                2. 你没有任何写操作能力:库存调整、改价、发货、售后处理等必须提示用户在系统页面人工操作。
                3. 金额均为原币金额,注意说明币种;不要自行换算汇率。
                4. 回答用中文,先给结论再给依据。""";

        /** 运营 Agent system prompt(库存/商品盘面工具) */
        private String opsPrompt = """
                你是电商 ERP 运营助手,专注库存与商品盘面。规则:
                1. 只能通过提供的只读工具查询数据,禁止编造或估算;查不到就如实说明。
                2. 你没有任何写操作能力:补货下单、库存调整等提示用户走采购/库存页面人工操作。
                3. 回答用中文,先给结论再给依据;涉及库存时说明口径(在库/占用/在途/可用)。""";

        /** ReAct 单轮最大思考-行动循环次数(防死循环护栏) */
        private int maxIters = 10;

        /** 历史重放截断:单轮重放的 USER/AI 文本行上限(取最近 N 行,防长会话上下文/token 膨胀;≤0 按 1) */
        private int historyMaxMessages = 40;
    }

    /**
     * 补货工作流参数组(#6 销量数据面落地后日均销量按真实动销重估;算法 V2 = (s,S) 策略 + 安全库存:
     * 补货点 = 提前期需求 + z×σ×√提前期,目标库存 = 提前期+覆盖期需求 + 安全库存;
     * 扫描护栏语义同 erp.alert)
     */
    @Getter
    @Setter
    public static class Replenish {

        /** 定时开关(erp-api ReplenishJob;cron 走 erp.ai.replenish.cron 占位符,不在此重复建键) */
        private boolean enabled = true;

        /** 低库存阈值:inventory.qty_available ≤ 此值参与补货建议 */
        private int lowStockThreshold = 10;

        /** 目标覆盖天数:V2 语义 = 提前期之外的额外覆盖天数(建议量补到目标库存 = 提前期+覆盖期需求+安全库存) */
        private int coverageDays = 14;

        /** 动销统计窗口(天,含今日,#6 2026-09-07 真实动销落地):日均销量 μ = 窗口内销量合计/窗口天数;
         *  需求波动 σ = 窗口逐日销量样本标准差(零销日补 0);窗口内零动销的 SKU 不再硬补(死 SKU 免每日建议) */
        private int salesWindowDays = 30;

        /** 最小建议量下限(仅对有动销的 SKU 起下限作用,建议量低于此值按此值) */
        private int minSuggestQty = 10;

        /** 采购提前期(天,V2):补货点 = 提前期需求 + 安全库存,库存位置 ≤ 补货点才触发建议 */
        private int leadTimeDays = 7;

        /** 服务水平(V2,0~1):安全库存 = z×σ×√提前期,z 按档位 {0.90,0.95,0.98,0.99} 最近邻映射 */
        private BigDecimal serviceLevel = new BigDecimal("0.95");

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

    /**
     * 采购建议工作流参数组(#17,2026-09-08):聚合口径 = 待确认补货缺口按"最新采购供应商"分组,
     * 预估金额 = Σ(最新采购单价×建议量);护栏语义同 replenish/anomaly
     */
    @Getter
    @Setter
    public static class Purchase {

        /** 单页扫描量(契约钳制 ≤100) */
        private int scanPageSize = 100;

        /** 单轮扫描行数上限 */
        private int scanMaxRows = 500;

        /** 单轮送 LLM 摘要的供应商组上限(超限按预估金额降序截断,未送评组走模板摘要) */
        private int llmMaxItems = 20;

        /** 摘要节点 system prompt(集中配置,docs/07 §9) */
        private String summaryPrompt = "你是电商 ERP 的采购分析助手。根据给定的按供应商聚合的补货缺口与预估金额,"
                + "为每个供应商写一句不超过 50 字的中文采购建议摘要(说明采购理由与紧急程度)。只输出 JSON 数组,"
                + "元素形如 {\"supplierId\":1,\"summary\":\"...\"},不输出任何其他文字。";
    }

    /**
     * 文案生成参数组(#17 三期候选「产品描述生成」,2026-09-08):为商品库启用商品批量生成
     * listing 文案建议(标题/五点描述/商品描述/关键词);降级语义与补货/异常/采购刻意不同——
     * 文案本体即 LLM 产出无模板可兜,LLM 不可用本轮零产出(degraded=true),不落垃圾建议
     */
    @Getter
    @Setter
    public static class Copy {

        /** 单页扫描量(契约钳制 ≤100) */
        private int scanPageSize = 100;

        /** 单轮扫描商品数上限(文案逐商品产长文本,上限比补货/采购收紧防长跑) */
        private int scanMaxRows = 200;

        /** 单轮送 LLM 生成的商品上限(超限按 productId 升序截断,截断商品下轮再生成不算降级) */
        private int llmMaxItems = 10;

        /** 生成节点 system prompt(集中配置,docs/07 §9) */
        private String prompt = "你是电商平台的 listing 文案专家。根据给定的商品信息(名称/品牌/类目/销售属性/SKU 规格)"
                + "为每个商品生成一套中文电商文案:标题 title(含品牌与核心卖点,60 字以内)、五点描述 bulletPoints"
                + "(5 条,每条不超过 40 字,突出卖点与规格)、商品描述 description(150~300 字)、搜索关键词 keywords"
                + "(5~10 个)。只能基于给定信息撰写,禁止编造商品没有的参数。只输出 JSON 数组,元素形如"
                + " {\"productId\":1,\"title\":\"...\",\"bulletPoints\":[\"...\"],\"description\":\"...\","
                + "\"keywords\":[\"...\"]},不输出任何其他文字。";
    }
}
