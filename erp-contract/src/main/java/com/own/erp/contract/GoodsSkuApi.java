package com.own.erp.contract;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : 内部商品 SKU 存在性契约(接口模块方案):erp-shop 人工绑定接口(bind)校验 sku_id 时调用,
 *         实现收口 erp-api(GoodsSkuApiImpl,查 erp-goods product_sku)——erp-shop 禁横向依赖 erp-goods(铁律 2)
 */
public interface GoodsSkuApi {

    /** 内部 SKU(product_sku.id)是否存在 */
    boolean existsSku(Long skuId);
}
