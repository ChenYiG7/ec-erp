package com.own.erp.contract.impl;

import com.own.erp.inventory.service.InventoryService;
import com.own.erp.order.service.ShopOrderService;
import com.own.erp.purchase.service.PurchaseOrderService;
import com.own.erp.shop.service.ShopProductService;
import com.own.erp.shop.service.ShopProductSkuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : GoodsReferenceApiImpl 单测(AIR:mock 各域 Service),覆盖跨域引用计数汇总(#5)
 */
class GoodsReferenceApiImplTest {

    private ShopProductSkuService shopProductSkuService;
    private ShopProductService shopProductService;
    private InventoryService inventoryService;
    private ShopOrderService shopOrderService;
    private PurchaseOrderService purchaseOrderService;
    private GoodsReferenceApiImpl api;

    @BeforeEach
    void setUp() {
        shopProductSkuService = mock(ShopProductSkuService.class);
        shopProductService = mock(ShopProductService.class);
        inventoryService = mock(InventoryService.class);
        shopOrderService = mock(ShopOrderService.class);
        purchaseOrderService = mock(PurchaseOrderService.class);
        api = new GoodsReferenceApiImpl(shopProductSkuService, shopProductService,
                inventoryService, shopOrderService, purchaseOrderService);
    }

    @Test
    void countSkuRefsSumsAllFourDomains() {
        when(shopProductSkuService.countBoundBySkuIds(List.of(7L))).thenReturn(1L);
        when(inventoryService.countBySkuIds(List.of(7L))).thenReturn(1L);
        when(shopOrderService.countItemRefsBySkuIds(List.of(7L))).thenReturn(2L);
        when(purchaseOrderService.countItemRefsBySkuIds(List.of(7L))).thenReturn(0L);

        assertEquals(4L, api.countSkuRefs(List.of(7L)));
    }

    @Test
    void countProductListingRefsDelegatesToShopProductService() {
        when(shopProductService.countListingByProductIds(List.of(1L))).thenReturn(2L);

        assertEquals(2L, api.countProductListingRefs(List.of(1L)));
    }
}
