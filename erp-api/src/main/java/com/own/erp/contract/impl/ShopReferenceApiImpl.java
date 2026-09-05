package com.own.erp.contract.impl;

import com.own.erp.aftersale.service.AftersaleOrderService;
import com.own.erp.contract.ShopReferenceApi;
import com.own.erp.order.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : ShopReferenceApi 实现(#3 店铺删除引用校验,接口模块方案的编排胶水,收口 erp-api):
 *         订单/售后引用计数取数走归属域 Service,本类只做跨域转接,不写业务(docs/07 §2.2,2026-09-04 拍板)
 */
@Component
@RequiredArgsConstructor
public class ShopReferenceApiImpl implements ShopReferenceApi {

    private final ShopOrderService shopOrderService;
    private final AftersaleOrderService aftersaleOrderService;

    @Override
    public long countOrderRefs(Collection<Long> shopIds) {
        return shopOrderService.countByShopIds(shopIds);
    }

    @Override
    public long countAftersaleRefs(Collection<Long> shopIds) {
        return aftersaleOrderService.countByShopIds(shopIds);
    }
}
