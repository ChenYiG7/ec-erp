package com.own.erp.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.platform.unified.UnifiedProduct;
import com.own.erp.shop.entity.ShopProduct;
import com.own.erp.shop.mapper.ShopProductMapper;
import com.own.erp.shop.mapper.ShopProductSkuMapper;
import com.own.erp.shop.request.query.ShopProductQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : ShopProductService 单测(AIR:mock Mapper,不依赖数据库);
 *     覆盖 #5 listing 同步:upsert 主流程、级联 SKU 行、无 seller_sku 行跳过、绑定字段不被覆盖由 SQL 保证(XML COALESCE/缺列)
 */
class ShopProductServiceTest {

    private ShopProductMapper shopProductMapper;
    private ShopProductSkuMapper shopProductSkuMapper;
    private ShopProductService shopProductService;

    @BeforeEach
    void setUp() {
        shopProductMapper = mock(ShopProductMapper.class);
        shopProductSkuMapper = mock(ShopProductSkuMapper.class);
        shopProductService = new ShopProductService(shopProductMapper, shopProductSkuMapper);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        ShopProduct shopProduct = new ShopProduct();
        shopProduct.setId(1L);
        when(shopProductMapper.selectById(1L)).thenReturn(shopProduct);
        assertEquals(1L, shopProductService.getById(1L).id());
        assertNull(shopProductService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        ShopProduct shopProduct = new ShopProduct();
        shopProduct.setId(2L);
        Page<ShopProduct> page = new Page<>(1, 10);
        page.setRecords(List.of(shopProduct));
        doReturn(page).when(shopProductMapper).selectPage(any(), any());
        assertEquals(2L, shopProductService.page(new ShopProductQuery()).getRecords().get(0).id());
    }

    @Test
    void saveUnifiedProductUpsertsAndCascadesSkuRows() {
        when(shopProductMapper.selectOne(any())).thenReturn(savedProduct(100L));

        UnifiedProduct product = new UnifiedProduct();
        product.setPlatformProductId("B0ABC123");
        product.setStatus("ACTIVE");
        UnifiedProduct.Sku skuA = new UnifiedProduct.Sku();
        skuA.setPlatformSkuId("ASIN-A");
        skuA.setSellerSku("SKU-A");
        skuA.setStock(8);
        skuA.setPrice(new BigDecimal("19.90"));
        skuA.setCurrency("USD");
        UnifiedProduct.Sku noCode = new UnifiedProduct.Sku();
        noCode.setSellerSku(" ");
        product.setSkus(List.of(skuA, noCode));

        Long id = shopProductService.saveUnifiedProduct(1L, product);

        assertEquals(100L, id);
        // 主表 upsert:单 SKU 商品回填 platform_sku_id,listing 状态与同步时间落库
        ArgumentCaptor<com.own.erp.shop.entity.ShopProduct> productCaptor =
                ArgumentCaptor.forClass(com.own.erp.shop.entity.ShopProduct.class);
        verify(shopProductMapper).upsert(productCaptor.capture());
        com.own.erp.shop.entity.ShopProduct saved = productCaptor.getValue();
        assertEquals(1L, saved.getShopId());
        assertEquals("B0ABC123", saved.getPlatformProductId());
        // 多 SKU 商品无法定位 platform_sku_id,落 NULL(仅单 SKU 商品回填,见下一用例)
        assertNull(saved.getPlatformSkuId());
        assertEquals("ACTIVE", saved.getListingStatus());
        assertNotNull(saved.getLastSyncAt());
        // 级联:有 seller_sku 的行进映射表(快照随行),无码行跳过
        ArgumentCaptor<com.own.erp.shop.entity.ShopProductSku> skuCaptor =
                ArgumentCaptor.forClass(com.own.erp.shop.entity.ShopProductSku.class);
        verify(shopProductSkuMapper, times(1)).upsert(skuCaptor.capture());
        assertEquals(100L, skuCaptor.getValue().getShopProductId());
        assertEquals("SKU-A", skuCaptor.getValue().getSellerSku());
        assertEquals(8, skuCaptor.getValue().getQuantity());
        assertEquals(new BigDecimal("19.90"), skuCaptor.getValue().getPrice());
        assertEquals("USD", skuCaptor.getValue().getCurrency());
    }

    @Test
    void singleSkuProductFillsPlatformSkuId() {
        when(shopProductMapper.selectOne(any())).thenReturn(savedProduct(100L));
        UnifiedProduct product = new UnifiedProduct();
        product.setPlatformProductId("B0XYZ789");
        UnifiedProduct.Sku only = new UnifiedProduct.Sku();
        only.setPlatformSkuId("ASIN-ONLY");
        only.setSellerSku("SKU-ONLY");
        product.setSkus(List.of(only));

        shopProductService.saveUnifiedProduct(1L, product);

        ArgumentCaptor<com.own.erp.shop.entity.ShopProduct> captor =
                ArgumentCaptor.forClass(com.own.erp.shop.entity.ShopProduct.class);
        verify(shopProductMapper).upsert(captor.capture());
        assertEquals("ASIN-ONLY", captor.getValue().getPlatformSkuId());
    }

    @Test
    void saveUnifiedProductRejectsMissingPlatformProductId() {
        UnifiedProduct product = new UnifiedProduct();
        product.setPlatformProductId(" ");

        assertThrows(BusinessException.class, () -> shopProductService.saveUnifiedProduct(1L, product));
        verify(shopProductMapper, never()).upsert(any());
    }

    @Test
    void saveUnifiedProductRejectsWhenRowMissingAfterUpsert() {
        when(shopProductMapper.selectOne(any())).thenReturn(null);

        UnifiedProduct product = new UnifiedProduct();
        product.setPlatformProductId("B0ABC123");
        assertThrows(BusinessException.class, () -> shopProductService.saveUnifiedProduct(1L, product));
        verify(shopProductSkuMapper, never()).upsert(any(com.own.erp.shop.entity.ShopProductSku.class));
    }

    private ShopProduct savedProduct(Long id) {
        ShopProduct saved = new ShopProduct();
        saved.setId(id);
        return saved;
    }
}
