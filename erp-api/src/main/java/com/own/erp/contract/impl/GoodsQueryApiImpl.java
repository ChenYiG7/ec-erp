package com.own.erp.contract.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.goods.entity.Brand;
import com.own.erp.goods.mapper.BrandMapper;
import com.own.erp.goods.request.query.ProductQuery;
import com.own.erp.goods.response.ProductResponse;
import com.own.erp.goods.response.ProductSkuResponse;
import com.own.erp.goods.service.ProductCategoryService;
import com.own.erp.goods.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : GoodsQueryApi 实现(#6 三期 AI 地基,编排胶水收口 erp-api,docs/07 §2.2):
 *         erp-ai 工具取数委托 erp-goods ProductService;entity→契约 record
 *         显式逐字段映射(经域 Response 中转,禁反射拷贝)。
 *         #17 文案生成扩容(2026-09-08,只加方法/字段不改语义):listSkusByProductId 委托
 *         ProductService;findCategoryNameById 委托 ProductCategoryService(类目域整域收口 Service);
 *         findBrandNameById 直连 BrandMapper——brand 为纯配置域,直连 Mapper 即该域合规口径(docs/07 §2.1,
 *         同 BrandController 先例),无 Service 可委托
 */
@Component
@RequiredArgsConstructor
public class GoodsQueryApiImpl implements GoodsQueryApi {

    private final ProductService productService;
    private final ProductCategoryService productCategoryService;
    private final BrandMapper brandMapper;

    @Override
    public QueryPage<ProductView> pageProducts(ProductFilter filter) {
        ProductQuery query = new ProductQuery();
        query.setKeyword(filter.keyword());
        query.setCategoryId(filter.categoryId());
        query.setStatus(filter.status());
        query.setPageNo(filter.page());
        query.setPageSize(filter.size());
        Page<ProductResponse> page = productService.pageProducts(query);
        List<ProductView> list = page.getRecords().stream().map(p -> ProductView.builder()
                .id(p.id())
                .spuCode(p.spuCode())
                .name(p.name())
                .categoryId(p.categoryId())
                .brandId(p.brandId())
                .attrsJson(p.attrsJson())
                .status(p.status())
                .build()).toList();
        return QueryPage.of(list, page.getTotal());
    }

    @Override
    public SkuView findSkuByCode(String skuCode) {
        ProductSkuResponse sku = productService.getSkuByCode(skuCode);
        return sku == null ? null : toSkuView(sku);
    }

    @Override
    public List<SkuView> listSkusByProductId(Long productId) {
        if (productId == null) {
            return List.of();
        }
        List<ProductSkuResponse> skus = productService.listSkusByProductId(productId);
        return CollUtil.isEmpty(skus) ? List.of()
                : skus.stream().map(GoodsQueryApiImpl::toSkuView).toList();
    }

    @Override
    public String findBrandNameById(Long brandId) {
        if (brandId == null) {
            return null;
        }
        Brand brand = brandMapper.selectById(brandId);
        return brand == null ? null : brand.getName();
    }

    @Override
    public String findCategoryNameById(Long categoryId) {
        return categoryId == null ? null : productCategoryService.findNameById(categoryId);
    }

    /** entity→契约 record 显式逐字段映射(经域 Response 中转,禁反射拷贝) */
    private static SkuView toSkuView(ProductSkuResponse sku) {
        return SkuView.builder()
                .id(sku.id())
                .productId(sku.productId())
                .skuCode(sku.skuCode())
                .barcode(sku.barcode())
                .attrsJson(sku.attrsJson())
                .costPrice(sku.costPrice())
                .weightG(sku.weightG())
                .hsCode(sku.hsCode())
                .battery(sku.battery())
                .status(sku.status())
                .build();
    }
}
