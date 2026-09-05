package com.own.erp.goods.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.GoodsReferenceApi;
import com.own.erp.goods.entity.Product;
import com.own.erp.goods.entity.ProductSku;
import com.own.erp.goods.mapper.ProductMapper;
import com.own.erp.goods.mapper.ProductSkuMapper;
import com.own.erp.goods.request.command.ProductSaveRequest;
import com.own.erp.goods.request.command.ProductSkuSaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : ProductService 单测(AIR:mock Mapper 与契约接口,不依赖数据库),
 *     覆盖 #5 收口:删除先查引用(绑定/库存/订单/采购/listing)有引用禁删 + skuCode 全局唯一前置查重
 */
class ProductServiceTest {

    private ProductMapper productMapper;
    private ProductSkuMapper skuMapper;
    private GoodsReferenceApi referenceApi;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productMapper = mock(ProductMapper.class);
        skuMapper = mock(ProductSkuMapper.class);
        referenceApi = mock(GoodsReferenceApi.class);
        productService = new ProductService(productMapper, skuMapper, referenceApi);
    }

    @Test
    void deleteSkuRejectedWhenReferenced() {
        when(referenceApi.countSkuRefs(List.of(7L))).thenReturn(2L);

        assertThrows(BusinessException.class, () -> productService.deleteSku(7L));
        verify(skuMapper, never()).deleteById(7L);
    }

    @Test
    void deleteSkuAllowedWhenUnreferenced() {
        when(referenceApi.countSkuRefs(List.of(7L))).thenReturn(0L);

        productService.deleteSku(7L);

        verify(skuMapper).deleteById(7L);
    }

    @Test
    void deleteProductRejectedWhenListingReferenced() {
        when(skuMapper.selectList(any())).thenReturn(List.of());
        when(referenceApi.countSkuRefs(List.of())).thenReturn(0L);
        when(referenceApi.countProductListingRefs(List.of(1L))).thenReturn(1L);

        assertThrows(BusinessException.class, () -> productService.deleteProduct(1L));
        verify(productMapper, never()).deleteById(1L);
    }

    @Test
    void deleteProductChecksChildSkuRefsBeforeDelete() {
        ProductSku child = new ProductSku();
        child.setId(7L);
        when(skuMapper.selectList(any())).thenReturn(List.of(child));
        when(referenceApi.countSkuRefs(List.of(7L))).thenReturn(3L);
        when(referenceApi.countProductListingRefs(List.of(1L))).thenReturn(0L);

        assertThrows(BusinessException.class, () -> productService.deleteProduct(1L));
        verify(productMapper, never()).deleteById(1L);
    }

    @Test
    void deleteProductAllowedWhenNoRefsAtAll() {
        when(skuMapper.selectList(any())).thenReturn(List.of());
        when(referenceApi.countSkuRefs(List.of())).thenReturn(0L);
        when(referenceApi.countProductListingRefs(List.of(1L))).thenReturn(0L);

        productService.deleteProduct(1L);

        verify(productMapper).deleteById(1L);
    }

    @Test
    void existsSkuChecksMapperAndHandlesNullArg() {
        when(skuMapper.selectById(7L)).thenReturn(new ProductSku());
        assertTrue(productService.existsSku(7L));
        when(skuMapper.selectById(404L)).thenReturn(null);
        assertFalse(productService.existsSku(404L));
        assertFalse(productService.existsSku(null));
        verifyNoInteractions(productMapper);
    }

    // ---------- skuCode 全局唯一(#5 收口) ----------

    private ProductSkuSaveRequest skuRequest(String skuCode) {
        return ProductSkuSaveRequest.builder()
                .skuCode(skuCode)
                .status(1)
                .build();
    }

    @Test
    void createSkuRejectedWhenSkuCodeExists() {
        when(skuMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> productService.createSku(skuRequest("SKU-001")));

        assertTrue(e.getMessage().contains("SKU-001"));
        verify(skuMapper, never()).insert(any(ProductSku.class));
    }

    @Test
    void createSkuRaceBackstopConvertsDuplicateKeyToBusinessException() {
        when(skuMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_sku")).when(skuMapper).insert(any(ProductSku.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> productService.createSku(skuRequest("SKU-001")));

        assertTrue(e.getMessage().contains("SKU编码已存在"));
    }

    @Test
    void createSkuAllowsUniqueCode() {
        when(skuMapper.selectCount(any())).thenReturn(0L);

        productService.createSku(skuRequest("SKU-001"));

        verify(skuMapper).insert(any(ProductSku.class));
    }

    @Test
    void updateSkuRejectedWhenSkuCodeTakenByOther() {
        when(skuMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> productService.updateSku(7L, skuRequest("SKU-001")));

        assertTrue(e.getMessage().contains("SKU-001"));
        verify(skuMapper, never()).updateById(any(ProductSku.class));
    }

    @Test
    void updateSkuAllowsCodeWhenCountZero() {
        when(skuMapper.selectCount(any())).thenReturn(0L);

        productService.updateSku(7L, skuRequest("SKU-001"));

        verify(skuMapper).updateById(any(ProductSku.class));
    }

    @Test
    void createProductRejectedWhenRequestContainsDuplicateSkuCodes() {
        when(productMapper.selectCount(any())).thenReturn(0L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> productService.createProduct(productRequest(), List.of(skuRequest("SKU-001"), skuRequest("SKU-001"))));

        assertTrue(e.getMessage().contains("请求内重复"));
        verify(skuMapper, never()).insert(any(ProductSku.class));
    }

    @Test
    void createProductRejectedWhenSkuCodeExistsInDb() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        when(skuMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> productService.createProduct(productRequest(), List.of(skuRequest("SKU-001"), skuRequest("SKU-002"))));

        assertTrue(e.getMessage().contains("SKU编码已存在"));
        verify(productMapper, never()).insert(any(Product.class));
    }

    @Test
    void createProductAllowsDistinctNewSkuCodes() {
        when(productMapper.selectCount(any())).thenReturn(0L);
        when(skuMapper.selectCount(any())).thenReturn(0L);

        productService.createProduct(productRequest(), List.of(skuRequest("SKU-001"), skuRequest("SKU-002")));

        // SPU 1 次 + 两个 SKU 各 1 次
        verify(productMapper).insert(any(Product.class));
        verify(skuMapper, times(2)).insert(any(ProductSku.class));
    }

    private ProductSaveRequest productRequest() {
        return ProductSaveRequest.builder()
                .spuCode("SPU-001")
                .name("测试商品")
                .build();
    }
}
