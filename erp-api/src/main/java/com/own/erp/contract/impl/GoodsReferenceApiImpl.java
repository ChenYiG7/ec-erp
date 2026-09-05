package com.own.erp.contract.impl;

import com.own.erp.contract.GoodsReferenceApi;
import com.own.erp.inventory.service.InventoryService;
import com.own.erp.order.service.ShopOrderService;
import com.own.erp.purchase.service.PurchaseOrderService;
import com.own.erp.shop.service.ShopProductService;
import com.own.erp.shop.service.ShopProductSkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : GoodsReferenceApi 实现(#5 删除引用校验,接口模块方案的编排胶水,收口 erp-api):
 *         引用计数取数走各归属域 Service(shop_product_sku 绑定 / inventory / shop_order_item /
 *         purchase_order_item / shop_product listing),本类只做跨域汇总,不写业务(docs/07 §2.2,2026-09-04 拍板)
 */
@Component
@RequiredArgsConstructor
public class GoodsReferenceApiImpl implements GoodsReferenceApi {

    private final ShopProductSkuService shopProductSkuService;
    private final ShopProductService shopProductService;
    private final InventoryService inventoryService;
    private final ShopOrderService shopOrderService;
    private final PurchaseOrderService purchaseOrderService;

    @Override
    public long countSkuRefs(Collection<Long> skuIds) {
        return shopProductSkuService.countBoundBySkuIds(skuIds)
                + inventoryService.countBySkuIds(skuIds)
                + shopOrderService.countItemRefsBySkuIds(skuIds)
                + purchaseOrderService.countItemRefsBySkuIds(skuIds);
    }

    @Override
    public long countProductListingRefs(Collection<Long> productIds) {
        return shopProductService.countListingByProductIds(productIds);
    }
}
