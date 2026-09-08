package com.own.erp.contract.impl;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : GoodsQueryApiImpl 单测(#6,AIR:mock 域 Service/Mapper,不依赖数据库):
 *     keyword/categoryId/status 过滤与分页参数映射、SPU/SKU 行视图显式映射、SKU 不存在透传 null;
 *     #17 文案生成扩容(2026-09-08):listSkusByProductId 委托与映射、brand/category 名称查找、
 *     空入参防触库
 */
class GoodsQueryApiImplTest {

    private ProductService productService;
    private ProductCategoryService productCategoryService;
    private BrandMapper brandMapper;
    private GoodsQueryApi goodsQueryApi;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        productCategoryService = mock(ProductCategoryService.class);
        brandMapper = mock(BrandMapper.class);
        goodsQueryApi = new GoodsQueryApiImpl(productService, productCategoryService, brandMapper);
    }

    @Test
    void pageProductsMapsFilterAndPagingIntoDomainQuery() {
        when(productService.pageProducts(any())).thenReturn(new Page<>(1, 20, 0));

        goodsQueryApi.pageProducts(GoodsQueryApi.ProductFilter.builder()
                .keyword("保温杯").categoryId(5L).status(1).pageNo(2).pageSize(30).build());

        ArgumentCaptor<ProductQuery> captor = ArgumentCaptor.forClass(ProductQuery.class);
        verify(productService).pageProducts(captor.capture());
        ProductQuery query = captor.getValue();
        assertEquals("保温杯", query.getKeyword());
        assertEquals(5L, query.getCategoryId());
        assertEquals(1, query.getStatus());
        assertEquals(2, query.getPageNo());
        assertEquals(30, query.getPageSize());
    }

    @Test
    void pageProductsMapsRowsAndTotal() {
        Page<ProductResponse> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(ProductResponse.builder()
                .id(1L)
                .spuCode("SPU-1")
                .name("保温杯")
                .categoryId(5L)
                .brandId(9L)
                .attrsJson("{\"材质\":\"不锈钢\"}")
                .status(1)
                .build()));
        when(productService.pageProducts(any())).thenReturn(page);

        QueryPage<GoodsQueryApi.ProductView> result = goodsQueryApi.pageProducts(
                GoodsQueryApi.ProductFilter.builder().build());

        assertEquals(1, result.total());
        GoodsQueryApi.ProductView view = result.list().get(0);
        assertEquals("SPU-1", view.spuCode());
        assertEquals("保温杯", view.name());
        assertEquals(9L, view.brandId());
        assertEquals("{\"材质\":\"不锈钢\"}", view.attrsJson());
    }

    @Test
    void findSkuByCodeMapsAllFields() {
        when(productService.getSkuByCode("SKU-1")).thenReturn(ProductSkuResponse.builder()
                .id(7L)
                .productId(1L)
                .skuCode("SKU-1")
                .barcode("6900000000001")
                .attrsJson("{\"颜色\":\"红\"}")
                .costPrice(new BigDecimal("12.5000"))
                .weightG(350)
                .hsCode("9617000000")
                .battery(0)
                .status(1)
                .build());

        GoodsQueryApi.SkuView view = goodsQueryApi.findSkuByCode("SKU-1");

        assertEquals(7L, view.id());
        assertEquals(1L, view.productId());
        assertEquals("SKU-1", view.skuCode());
        assertEquals("{\"颜色\":\"红\"}", view.attrsJson());
        assertEquals(0, new BigDecimal("12.5000").compareTo(view.costPrice()));
        assertEquals(350, view.weightG());
        assertEquals("9617000000", view.hsCode());
        assertEquals(0, view.battery());
        assertEquals(1, view.status());
    }

    @Test
    void findSkuByCodeReturnsNullWhenMissing() {
        when(productService.getSkuByCode("NOPE")).thenReturn(null);
        assertNull(goodsQueryApi.findSkuByCode("NOPE"));
    }

    @Test
    void listSkusByProductIdDelegatesAndMaps() {
        when(productService.listSkusByProductId(1L)).thenReturn(List.of(
                ProductSkuResponse.builder().id(7L).productId(1L).skuCode("SKU-1")
                        .attrsJson("{\"颜色\":\"红\"}").weightG(350).battery(0).status(1).build(),
                ProductSkuResponse.builder().id(8L).productId(1L).skuCode("SKU-2")
                        .weightG(500).battery(1).status(1).build()));

        List<GoodsQueryApi.SkuView> skus = goodsQueryApi.listSkusByProductId(1L);

        assertEquals(2, skus.size());
        assertEquals("{\"颜色\":\"红\"}", skus.get(0).attrsJson());
        assertNull(skus.get(1).attrsJson());
        assertEquals(1, skus.get(1).battery());
    }

    @Test
    void listSkusByProductIdGuardsNullAndEmpty() {
        assertTrue(goodsQueryApi.listSkusByProductId(null).isEmpty());
        when(productService.listSkusByProductId(1L)).thenReturn(List.of());
        assertTrue(goodsQueryApi.listSkusByProductId(1L).isEmpty());
    }

    @Test
    void findBrandAndCategoryNamesDelegate() {
        Brand brand = new Brand();
        brand.setId(9L);
        brand.setName("某品牌");
        when(brandMapper.selectById(9L)).thenReturn(brand);
        when(productCategoryService.findNameById(5L)).thenReturn("家居");

        assertEquals("某品牌", goodsQueryApi.findBrandNameById(9L));
        assertEquals("家居", goodsQueryApi.findCategoryNameById(5L));
        assertNull(goodsQueryApi.findBrandNameById(null));
        assertNull(goodsQueryApi.findCategoryNameById(null));
        when(brandMapper.selectById(404L)).thenReturn(null);
        assertNull(goodsQueryApi.findBrandNameById(404L));
    }
}
