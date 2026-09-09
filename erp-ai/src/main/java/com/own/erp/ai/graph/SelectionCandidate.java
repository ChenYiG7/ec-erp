package com.own.erp.ai.graph;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 选品评分候选行(#17 落位表「智能选品」,collect 装配 → score 回填维度分 →
 *     summarize 回填摘要 → persist 落库):行级全量字段一次装配,节点间经 OverAllState 传递;
 *     金额一律 CNY(利润排行口径 salesCny/profitCny 已折算,汇率缺行跳过=#19③ 缺口纪律)。
 *     维度分 salesDim/trendDim/marginDim 与综合分 score 均为 0~100(BigDecimal,1 位小数);
 *     数据缺口不猜值:毛利缺失 marginDim 记中性 50 并打 MARGIN_MISSING 标记(docs/07 §8 禁静默归零同源)
 */
@Builder(toBuilder = true)
public record SelectionCandidate(

        /** 内部SKU ID(product_sku.id) */
        Long skuId,

        /** 内部 SKU 编码 */
        String skuCode,

        /** 所属商品名称(SPU 名快照) */
        String productName,

        /** 跨仓合并可用库存(InventoryQueryApi qtyAvailable Σ) */
        int qtyAvailable,

        /** 跨仓合并在途库存(qtyTransit Σ) */
        int qtyTransit,

        /** 近 30 天销量合计(order_sales_daily 支付日口径) */
        int qty30,

        /** 近 7 天销量合计(趋势"近期"半窗) */
        int recent7,

        /** 前 7 天销量合计(趋势"对照"半窗,今日往前第 8~14 天) */
        int prior7,

        /** 近 30 天售价合计(CNY,利润排行行;null=该 SKU 不在排行=无已绑定订单行) */
        BigDecimal salesCny,

        /** 近 30 天利润合计(CNY;null=无利润数据) */
        BigDecimal profitCny,

        /** 毛利率 = profitCny/salesCny(30d 窗口;null=利润数据缺失,salesCny 非正也置 null 禁除零) */
        BigDecimal margin,

        /** 销量规模维度分(0~100) */
        BigDecimal salesDim,

        /** 动销趋势维度分(0~100) */
        BigDecimal trendDim,

        /** 毛利率维度分(0~100;缺数据=中性 50) */
        BigDecimal marginDim,

        /** 综合评分 = Σ 权重×维度分(权重归一化后) */
        BigDecimal score,

        /** 风险等级 LOW/MID/HIGH(score 节点规则定级) */
        String risk,

        /** 数据缺口/风险标记(MARGIN_MISSING/NO_SALES 等,人工判读与单测断言用) */
        List<String> flags,

        /** 建议摘要(LLM 一句话,降级走模板) */
        String summary,

        /** 摘要是否 LLM 产出(false=模板降级) */
        boolean llmScored
) {

    /** 数据缺口标记:利润排行未覆盖(不在 top100/无已绑定订单行),毛利维度记中性 */
    public static final String FLAG_MARGIN_MISSING = "MARGIN_MISSING";

    /** 风险标记:有近期动销但可用 ≤0(断货丢量) */
    public static final String FLAG_STOCKOUT = "STOCKOUT";

    /** 风险标记:在卖且毛利为负(亏损单) */
    public static final String FLAG_LOSS_MAKING = "LOSS_MAKING";

    /** 风险标记:30 天零动销(纯库存滞销,综合分天然垫底一般不入选) */
    public static final String FLAG_NO_SALES = "NO_SALES";
}
