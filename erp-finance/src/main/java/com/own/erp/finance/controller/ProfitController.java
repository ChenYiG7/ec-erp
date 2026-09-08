package com.own.erp.finance.controller;

import com.own.erp.common.api.Result;
import com.own.erp.contract.OrderProfitQuery;
import com.own.erp.contract.OrderProfitRow;
import com.own.erp.contract.OrderProfitSummary;
import com.own.erp.contract.ProfitQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.finance.request.query.OrderProfitPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : 实时销售利润接口(#19③ 三口径第一层):只读报表,登录即可(利润是店铺经营数据,
 *     行级权限随多商户四期);取数走 ProfitQueryApi 契约(实现收口 erp-api,@Lazy 断构造环
 *     docs/07 §2.2 同 PurchaseInboundService 先例)——前端报表页与 AI 工具共用同一契约口径
 */
@Tag(name = "实时销售利润", description = "订单口径:利润 = 售价 − 出库成本 − 平台佣金;成本移动加权,汇率按下单日回溯")
@RestController
@RequestMapping("/api/finance/profit")
@RequiredArgsConstructor
public class ProfitController {

    private final ProfitQueryApi profitQueryApi;

    @Operation(summary = "订单行利润分页", description = "已支付态三态,下单时间倒序;缺成本=未出库,缺佣金=待结算,缺汇率=本位币列为空")
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public Result<QueryPage<OrderProfitRow>> page(OrderProfitPageQuery query) {
        return Result.ok(profitQueryApi.pageOrderProfit(toContract(query)));
    }

    @Operation(summary = "利润汇总", description = "同条件全量聚合;缺口单独计数(缺汇率/未出库/待结算)不静默归零")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/summary")
    public Result<OrderProfitSummary> summarize(OrderProfitPageQuery query) {
        return Result.ok(profitQueryApi.summarize(toContract(query)));
    }

    /** 域内 GET 入参 → 契约查询模型(pageNo/pageSize 语义一致,钳制随契约) */
    private OrderProfitQuery toContract(OrderProfitPageQuery query) {
        return new OrderProfitQuery(query.getShopId(), query.getPlatform(), query.getSkuId(),
                query.getDateFrom(), query.getDateTo(), query.getPageNo(), query.getPageSize());
    }
}
