package com.own.erp.contract.impl;

import com.own.erp.contract.CurrentUserApi;
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
 *         契约转委托 erp-finance ProfitQueryService(利润组装正本),无自建逻辑。
 *         数据权限(#27①):HTTP 链路(前端利润页与 AI 工具都经本实现)强制装配
 *         CurrentUserApi.currentShopIds() 重构查询入参;系统内部调用不经本实现
 *         (ProfitPeriodReportService 直调 Service,shopIds=null 不受数据权限约束)
 */
@Component
@RequiredArgsConstructor
public class ProfitQueryApiImpl implements ProfitQueryApi {

    private final ProfitQueryService profitQueryService;
    private final CurrentUserApi currentUserApi;

    @Override
    public QueryPage<OrderProfitRow> pageOrderProfit(OrderProfitQuery query) {
        return profitQueryService.page(withShopScope(query));
    }

    @Override
    public OrderProfitSummary summarize(OrderProfitQuery query) {
        return profitQueryService.summarize(withShopScope(query));
    }

    @Override
    public List<ProfitDailyTrendRow> listDailyTrend(OrderProfitQuery query) {
        return profitQueryService.listDailyTrend(withShopScope(query));
    }

    @Override
    public List<ProfitSkuRankRow> listSkuProfitRank(OrderProfitQuery query, int topN) {
        return profitQueryService.listSkuProfitRank(withShopScope(query), topN);
    }

    /** 覆盖装配数据权限店铺集(record 不可变,重建副本;空集=不可见任何店铺,由 Service 短路) */
    private OrderProfitQuery withShopScope(OrderProfitQuery query) {
        return new OrderProfitQuery(query.shopId(), query.platform(), query.skuId(),
                query.dateFrom(), query.dateTo(), query.pageNo(), query.pageSize(),
                currentUserApi.currentShopIds());
    }
}
