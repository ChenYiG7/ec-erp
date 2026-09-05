package com.own.erp.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.shop.entity.ShopProductSku;
import com.own.erp.shop.mapper.ShopProductSkuMapper;
import com.own.erp.shop.request.query.ShopProductSkuQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : ShopProductSkuService 单测(AIR:mock Mapper,不依赖数据库),覆盖人工绑定与幂等;自动匹配测试随 #5 补齐
 */
class ShopProductSkuServiceTest {

    private ShopProductSkuMapper shopProductSkuMapper;
    private GoodsSkuApi goodsSkuApi;
    private ShopProductSkuService shopProductSkuService;

    @BeforeEach
    void setUp() {
        shopProductSkuMapper = mock(ShopProductSkuMapper.class);
        goodsSkuApi = mock(GoodsSkuApi.class);
        shopProductSkuService = new ShopProductSkuService(shopProductSkuMapper, goodsSkuApi);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        ShopProductSku mapping = new ShopProductSku();
        mapping.setId(1L);
        when(shopProductSkuMapper.selectById(1L)).thenReturn(mapping);
        assertEquals(1L, shopProductSkuService.getById(1L).id());
        assertNull(shopProductSkuService.getById(404L));
    }

    @Test
    void pageMapsRecordsToResponse() {
        ShopProductSku mapping = new ShopProductSku();
        mapping.setId(2L);
        Page<ShopProductSku> page = new Page<>(1, 10);
        page.setRecords(List.of(mapping));
        doReturn(page).when(shopProductSkuMapper).selectPage(any(), any());
        assertEquals(2L, shopProductSkuService.page(new ShopProductSkuQuery()).getRecords().get(0).id());
    }

    @Test
    void bindFillsSkuIdAndSetsManualStatus() {
        when(goodsSkuApi.existsSku(99L)).thenReturn(true);
        ShopProductSku mapping = new ShopProductSku();
        mapping.setId(1L);
        mapping.setSkuId(null);
        mapping.setMatchStatus(0);
        when(shopProductSkuMapper.selectById(1L)).thenReturn(mapping);

        shopProductSkuService.bind(1L, 99L);

        ArgumentCaptor<ShopProductSku> captor = ArgumentCaptor.forClass(ShopProductSku.class);
        verify(shopProductSkuMapper).updateById(captor.capture());
        assertEquals(99L, captor.getValue().getSkuId());
        assertEquals(ShopProductSkuService.MATCH_MANUAL, captor.getValue().getMatchStatus());
    }

    @Test
    void bindSameSkuIsIdempotentAndSkipsUpdate() {
        when(goodsSkuApi.existsSku(99L)).thenReturn(true);
        ShopProductSku mapping = new ShopProductSku();
        mapping.setId(1L);
        mapping.setSkuId(99L);
        mapping.setMatchStatus(ShopProductSkuService.MATCH_MANUAL);
        when(shopProductSkuMapper.selectById(1L)).thenReturn(mapping);

        shopProductSkuService.bind(1L, 99L);

        verify(shopProductSkuMapper, never()).updateById(any(ShopProductSku.class));
    }

    @Test
    void bindRejectsUnknownSkuId() {
        // #5 收口:sku_id 存在性经 erp-contract 接口校验,查无此 SKU 禁绑定
        when(goodsSkuApi.existsSku(404L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> shopProductSkuService.bind(1L, 404L));
        verifyNoInteractions(shopProductSkuMapper);
    }

    @Test
    void bindRejectsMissingMappingRecord() {
        when(goodsSkuApi.existsSku(99L)).thenReturn(true);
        when(shopProductSkuMapper.selectById(404L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> shopProductSkuService.bind(404L, 99L));
        verify(shopProductSkuMapper).selectById(404L);
        verify(shopProductSkuMapper, never()).updateById(any(ShopProductSku.class));
    }

    @Test
    void bindRejectsNullSkuId() {
        assertThrows(BusinessException.class, () -> shopProductSkuService.bind(1L, null));
        verifyNoInteractions(shopProductSkuMapper);
    }

    @Test
    void mapSellerSkuToSkuIdReturnsBoundMapping() {
        ShopProductSku bound = new ShopProductSku();
        bound.setSellerSku("SKU-A");
        bound.setSkuId(99L);
        when(shopProductSkuMapper.selectBoundByShopAndSellerSkus(1L, Set.of("SKU-A", "SKU-B")))
                .thenReturn(List.of(bound));

        Map<String, Long> mapping = shopProductSkuService.mapSellerSkuToSkuId(1L, Set.of("SKU-A", "SKU-B"));

        assertEquals(Map.of("SKU-A", 99L), mapping);
    }

    @Test
    void mapSellerSkuToSkuIdShortCircuitsOnEmptyInput() {
        assertEquals(Map.of(), shopProductSkuService.mapSellerSkuToSkuId(1L, Set.of()));
        assertEquals(Map.of(), shopProductSkuService.mapSellerSkuToSkuId(null, Set.of("SKU-A")));
        verifyNoInteractions(shopProductSkuMapper);
    }

    @Test
    void autoMatchFillsOnlyUnboundRowsAndKeepsUnknownPending() {
        ShopProductSku unboundA = new ShopProductSku();
        unboundA.setId(11L);
        unboundA.setSellerSku("SKU-A");
        ShopProductSku unboundC = new ShopProductSku();
        unboundC.setId(12L);
        unboundC.setSellerSku("SKU-C");
        when(shopProductSkuMapper.selectUnboundByShop(1L)).thenReturn(List.of(unboundA, unboundC));

        int matched = shopProductSkuService.autoMatch(1L, Map.of("SKU-A", 99L));

        assertEquals(1, matched);
        ArgumentCaptor<ShopProductSku> captor = ArgumentCaptor.forClass(ShopProductSku.class);
        verify(shopProductSkuMapper).updateById(captor.capture());
        assertEquals(11L, captor.getValue().getId());
        assertEquals(99L, captor.getValue().getSkuId());
        assertEquals(ShopProductSkuService.MATCH_AUTO, captor.getValue().getMatchStatus());
        // SKU-C 无码命中:保持待匹配,全程仅一次更新
        verify(shopProductSkuMapper, times(1)).updateById(any(ShopProductSku.class));
    }

    @Test
    void autoMatchReturnsZeroOnEmptyInput() {
        assertEquals(0, shopProductSkuService.autoMatch(1L, Map.of()));
        assertEquals(0, shopProductSkuService.autoMatch(null, Map.of("SKU-A", 99L)));
        verifyNoInteractions(shopProductSkuMapper);
    }
}
