package com.own.erp.contract.impl;

import com.own.erp.contract.SalesQueryApi;
import com.own.erp.order.service.OrderSalesDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : SalesQueryApi 实现(#6 销量数据面,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 读销量委托 erp-order OrderSalesDailyService(order_sales_daily 日统计表)
 */
@Component
@RequiredArgsConstructor
public class SalesQueryApiImpl implements SalesQueryApi {

    private final OrderSalesDailyService orderSalesDailyService;

    @Override
    public Map<Long, Integer> sumQtyBySku(Collection<Long> skuIds, int trailingDays) {
        return orderSalesDailyService.sumQtyBySku(skuIds, trailingDays);
    }

    @Override
    public Map<Long, Map<LocalDate, Integer>> listDailyQtyBySku(Collection<Long> skuIds, int trailingDays) {
        return orderSalesDailyService.listDailyQtyBySku(skuIds, trailingDays);
    }
}
