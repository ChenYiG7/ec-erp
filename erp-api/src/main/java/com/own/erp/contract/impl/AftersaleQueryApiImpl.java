package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.aftersale.request.query.AftersaleOrderQuery;
import com.own.erp.aftersale.response.AftersaleOrderResponse;
import com.own.erp.aftersale.service.AftersaleOrderService;
import com.own.erp.contract.AftersaleQueryApi;
import com.own.erp.contract.QueryPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : AftersaleQueryApi 实现(#6 三期 AI 地基,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 工具取数委托 erp-aftersale AftersaleOrderService.page;entity→契约 record
 *         显式逐字段映射(经域 Response 中转,禁反射拷贝);本契约只读,售后处理动作不经此
 */
@Component
@RequiredArgsConstructor
public class AftersaleQueryApiImpl implements AftersaleQueryApi {

    private final AftersaleOrderService aftersaleOrderService;

    @Override
    public QueryPage<AftersaleView> pageAftersales(AftersaleFilter filter) {
        AftersaleOrderQuery query = new AftersaleOrderQuery();
        query.setShopId(filter.shopId());
        query.setStatus(filter.status());
        query.setType(filter.type());
        query.setOrderId(filter.orderId());
        query.setPageNo(filter.page());
        query.setPageSize(filter.size());
        Page<AftersaleOrderResponse> page = aftersaleOrderService.page(query);
        List<AftersaleView> list = page.getRecords().stream().map(r -> AftersaleView.builder()
                .id(r.id())
                .aftersaleNo(r.aftersaleNo())
                .shopId(r.shopId())
                .orderId(r.orderId())
                .warehouseId(r.warehouseId())
                .type(r.type())
                .status(r.status())
                .refundAmount(r.refundAmount())
                .currency(r.currency())
                .reason(r.reason())
                .result(r.result())
                .createdAt(r.createdAt())
                .build()).toList();
        return QueryPage.of(list, page.getTotal());
    }
}
