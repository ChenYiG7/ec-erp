package com.own.erp.contract.impl;

import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.goods.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : GoodsSkuApi 实现(接口模块方案的编排胶水,收口 erp-api):
 *         erp-shop 人工绑定接口校验 sku_id 存在性,取数走 erp-goods ProductService(docs/07 §2.2,2026-09-04 拍板)
 */
@Component
@RequiredArgsConstructor
public class GoodsSkuApiImpl implements GoodsSkuApi {

    private final ProductService productService;

    @Override
    public boolean existsSku(Long skuId) {
        return productService.existsSku(skuId);
    }
}
