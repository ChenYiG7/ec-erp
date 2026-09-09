package com.own.erp.contract.impl;

import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitRow;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.contract.ProfitDailyTrendRow;
import com.own.erp.contract.ProfitQueryApi;
import com.own.erp.contract.ProfitSkuRankRow;
import com.own.erp.contract.QueryPage;
import com.own.erp.finance.service.ProfitQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : ProfitQueryApi 实现(#19③,接口模块方案编排胶水收口 erp-api):
 *         契约转委托 erp-finance ProfitQueryService(利润组装正本),无自建逻辑
 */
@Component
@RequiredArgsConstructor
public class ProfitQueryApiImpl implements ProfitQueryApi {

    private final ProfitQueryService profitQueryService;

    @Override
    public QueryPage<OrderProfitRow> pageOrderProfit(OrderProfitQuery query) {
        return profitQueryService.page(query);
    }

    @Override
    public OrderProfitSummary summarize(OrderProfitQuery query) {
        return profitQueryService.summarize(query);
    }

    @Override
    public List<ProfitDailyTrendRow> listDailyTrend(OrderProfitQuery query) {
        return profitQueryService.listDailyTrend(query);
    }

    @Override
    public List<ProfitSkuRankRow> listSkuProfitRank(OrderProfitQuery query, int topN) {
        return profitQueryService.listSkuProfitRank(query, topN);
    }
}
