package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 实时销售利润只读查询契约(#19③,查询契约第六件,三口径第一层 docs/02 §14):
 *         订单口径 SKU 级利润 = 售价 − 成本 − 平台佣金(− 退款 V2),不落库实时算(小时级新鲜度天然满足);
 *         成本先移动加权(基于 inventory_flow OUT_SHIP 快照),汇率按下单日回溯 exchange_rate;
 *         实现收口 erp-api(ProfitQueryApiImpl,委托 erp-finance ProfitQueryService)——
 *         前端报表页与 AI 工具(三期 ACOS/定价)共用本契约
 */
public interface ProfitQueryApi {

    /**
     * 订单行利润分页(已支付态三态,下单时间倒序)
     */
    QueryPage<OrderProfitRow> pageOrderProfit(OrderProfitQuery query);

    /**
     * 同条件汇总(全量行聚合,与行口径一致;缺口单独计数不静默归零)
     */
    OrderProfitSummary summarize(OrderProfitQuery query);
}
