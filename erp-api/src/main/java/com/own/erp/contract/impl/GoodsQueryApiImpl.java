package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.goods.request.query.ProductQuery;
import com.own.erp.goods.response.ProductResponse;
import com.own.erp.goods.response.ProductSkuResponse;
import com.own.erp.goods.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : GoodsQueryApi 实现(#6 三期 AI 地基,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 工具取数委托 erp-goods ProductService;entity→契约 record
 *         显式逐字段映射(经域 Response 中转,禁反射拷贝)
 */
@Component
@RequiredArgsConstructor
public class GoodsQueryApiImpl implements GoodsQueryApi {

    private final ProductService productService;

    @Override
    public QueryPage<ProductView> pageProducts(ProductFilter filter) {
        ProductQuery query = new ProductQuery();
        query.setKeyword(filter.keyword());
        query.setCategoryId(filter.categoryId());
        query.setPageNo(filter.page());
        query.setPageSize(filter.size());
        Page<ProductResponse> page = productService.pageProducts(query);
        List<ProductView> list = page.getRecords().stream().map(p -> ProductView.builder()
                .id(p.id())
                .spuCode(p.spuCode())
                .name(p.name())
                .categoryId(p.categoryId())
                .brandId(p.brandId())
                .status(p.status())
                .build()).toList();
        return QueryPage.of(list, page.getTotal());
    }

    @Override
    public SkuView findSkuByCode(String skuCode) {
        ProductSkuResponse sku = productService.getSkuByCode(skuCode);
        if (sku == null) {
            return null;
        }
        return SkuView.builder()
                .id(sku.id())
                .productId(sku.productId())
                .skuCode(sku.skuCode())
                .barcode(sku.barcode())
                .costPrice(sku.costPrice())
                .weightG(sku.weightG())
                .hsCode(sku.hsCode())
                .battery(sku.battery())
                .status(sku.status())
                .build();
    }
}
