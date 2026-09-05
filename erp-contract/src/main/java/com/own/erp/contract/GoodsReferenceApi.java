package com.own.erp.contract;

import java.util.Collection;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 内部商品(SKU/SPU)外部引用计数契约(#5 删除校验,接口模块方案):
 *         erp-goods 删 SKU/SPU 前调用,实现收口 erp-api(GoodsReferenceApiImpl)——
 *         引用侧计数取数在各归属域 Service(shop_product_sku 绑定 / inventory / shop_order_item / purchase_order_item /
 *         shop_product listing),本模块零依赖零实现,erp-goods 禁横向依赖引用侧模块(铁律 2)
 */
public interface GoodsReferenceApi {

    /**
     * SKU 被外部域引用的行数合计:绑定行(shop_product_sku.sku_id)+ 库存行(inventory.sku_id)
     * + 订单明细(shop_order_item.sku_id)+ 采购明细(purchase_order_item.sku_id);>0 即禁删
     */
    long countSkuRefs(Collection<Long> skuIds);

    /** SPU 被 listing 引用的行数(shop_product.product_id,即任一店铺同步过该商品);>0 即禁删 */
    long countProductListingRefs(Collection<Long> productIds);
}
