package com.own.erp.goods.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.goods.entity.ProductCategory;
import com.own.erp.goods.mapper.ProductCategoryMapper;
import com.own.erp.goods.mapper.ProductMapper;
import com.own.erp.goods.request.command.ProductCategorySaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : ProductCategoryService #7 收口单测(AIR:mock Mapper,不依赖数据库):
 *     父分类存在性 / 成环校验(自身与自身子孙禁挂,脏数据环自保护)/ 删除前子分类与商品引用拦截
 */
class ProductCategoryServiceTest {

    private ProductCategoryMapper categoryMapper;
    private ProductMapper productMapper;
    private ProductCategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(ProductCategoryMapper.class);
        productMapper = mock(ProductMapper.class);
        categoryService = new ProductCategoryService(categoryMapper, productMapper);
    }

    private ProductCategorySaveRequest request(Long parentId) {
        return ProductCategorySaveRequest.builder()
                .parentId(parentId)
                .name("分类A")
                .sort(0)
                .status(1)
                .build();
    }

    private ProductCategory category(Long id, Long parentId) {
        ProductCategory category = new ProductCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setName("分类" + id);
        return category;
    }

    // ---------- 父分类存在性 ----------

    @Test
    void createDefaultsToRootWhenParentAbsent() {
        categoryService.create(ProductCategorySaveRequest.builder().name("根分类").build());

        verify(categoryMapper).insert(any(ProductCategory.class));
    }

    @Test
    void createRejectedWhenParentNotExist() {
        when(categoryMapper.selectById(9L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> categoryService.create(request(9L)));

        assertTrue(e.getMessage().contains("父分类不存在"));
        verify(categoryMapper, never()).insert(any(ProductCategory.class));
    }

    @Test
    void createAllowedWhenParentExists() {
        when(categoryMapper.selectById(9L)).thenReturn(category(9L, 0L));

        categoryService.create(request(9L));

        verify(categoryMapper).insert(any(ProductCategory.class));
    }

    @Test
    void updateRejectedWhenParentNotExist() {
        when(categoryMapper.selectById(9L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> categoryService.update(5L, request(9L)));

        verify(categoryMapper, never()).updateById(any(ProductCategory.class));
    }

    // ---------- 成环校验 ----------

    @Test
    void updateRejectedWhenParentIsSelf() {
        BusinessException e = assertThrows(BusinessException.class, () -> categoryService.update(5L, request(5L)));

        assertTrue(e.getMessage().contains("自己或自己的子孙"));
        verify(categoryMapper, never()).updateById(any(ProductCategory.class));
    }

    @Test
    void updateRejectedWhenHangingUnderOwnDescendant() {
        // 链:6 的父是 5(被更新节点)。把 5 挂到 6 下 = 挂到自己子孙下,成环
        when(categoryMapper.selectById(6L)).thenReturn(category(6L, 5L));

        BusinessException e = assertThrows(BusinessException.class, () -> categoryService.update(5L, request(6L)));

        assertTrue(e.getMessage().contains("自己或自己的子孙"));
        verify(categoryMapper, never()).updateById(any(ProductCategory.class));
    }

    @Test
    void updateRejectedWhenExistingDataAlreadyCyclic() {
        // 存量脏数据:7↔8 互为父子。把新分类挂上时不死循环,直接拒
        when(categoryMapper.selectById(7L)).thenReturn(category(7L, 8L));
        when(categoryMapper.selectById(8L)).thenReturn(category(8L, 7L));

        BusinessException e = assertThrows(BusinessException.class, () -> categoryService.create(request(7L)));

        assertTrue(e.getMessage().contains("环"));
        verify(categoryMapper, never()).insert(any(ProductCategory.class));
    }

    @Test
    void updateAllowedWhenReparentToRoot() {
        categoryService.update(5L, request(0L));

        verify(categoryMapper).updateById(any(ProductCategory.class));
    }

    @Test
    void updateAllowedWhenParentChainClean() {
        // 链:6 的父是 0。5 挂到 6 下合法
        when(categoryMapper.selectById(6L)).thenReturn(category(6L, 0L));

        categoryService.update(5L, request(6L));

        verify(categoryMapper).updateById(any(ProductCategory.class));
    }

    // ---------- 删除引用校验 ----------

    @Test
    void deleteRejectedWhenChildCategoryExists() {
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class, () -> categoryService.delete(5L));

        assertTrue(e.getMessage().contains("子分类"));
        verify(categoryMapper, never()).deleteById(5L);
    }

    @Test
    void deleteRejectedWhenProductReferenced() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectCount(any())).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> categoryService.delete(5L));

        assertTrue(e.getMessage().contains("商品"));
        verify(categoryMapper, never()).deleteById(5L);
    }

    @Test
    void deleteAllowedWhenNoChildAndNoProductRef() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(productMapper.selectCount(any())).thenReturn(0L);

        categoryService.delete(5L);

        verify(categoryMapper).deleteById(5L);
    }
}
