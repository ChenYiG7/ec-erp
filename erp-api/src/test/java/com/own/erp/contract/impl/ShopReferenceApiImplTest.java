package com.own.erp.contract.impl;

import com.own.erp.aftersale.service.AftersaleOrderService;
import com.own.erp.order.service.ShopOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : ShopReferenceApiImpl 单测(AIR:mock 各域 Service),覆盖店铺删除跨域引用计数转接(#3)
 */
class ShopReferenceApiImplTest {

    private ShopOrderService shopOrderService;
    private AftersaleOrderService aftersaleOrderService;
    private ShopReferenceApiImpl api;

    @BeforeEach
    void setUp() {
        shopOrderService = mock(ShopOrderService.class);
        aftersaleOrderService = mock(AftersaleOrderService.class);
        api = new ShopReferenceApiImpl(shopOrderService, aftersaleOrderService);
    }

    @Test
    void countOrderRefsDelegatesToShopOrderService() {
        when(shopOrderService.countByShopIds(List.of(1L))).thenReturn(5L);
        assertEquals(5L, api.countOrderRefs(List.of(1L)));
    }

    @Test
    void countAftersaleRefsDelegatesToAftersaleOrderService() {
        when(aftersaleOrderService.countByShopIds(List.of(1L))).thenReturn(2L);
        assertEquals(2L, api.countAftersaleRefs(List.of(1L)));
    }
}
