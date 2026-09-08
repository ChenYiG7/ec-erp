package com.own.erp.contract.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.ShopQueryApi;
import com.own.erp.shop.request.query.ShopQuery;
import com.own.erp.shop.response.ShopResponse;
import com.own.erp.shop.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : ShopQueryApiImpl 单测(#6 tools 扩容,AIR:mock ShopService,不依赖数据库):
 *     过滤/分页参数映射(含默认归一)、行视图显式映射(凭证字段不进视图)、店铺不存在透传 null
 */
class ShopQueryApiImplTest {

    private ShopService shopService;
    private ShopQueryApi shopQueryApi;

    @BeforeEach
    void setUp() {
        shopService = mock(ShopService.class);
        shopQueryApi = new ShopQueryApiImpl(shopService);
    }

    private ShopResponse shop() {
        return ShopResponse.builder()
                .id(1L)
                .merchantId(1L)
                .platform("AMAZON")
                .shopName("测试店铺")
                .sellerId("SELLER-1")
                .appKey("APP-KEY")
                .accessToken("c2VjcmV0***")
                .tokenExpireAt(LocalDateTime.of(2026, 12, 1, 0, 0))
                .status(1)
                .build();
    }

    @Test
    void pageShopsMapsFilterAndPagingIntoDomainQuery() {
        when(shopService.pageShops(any())).thenReturn(new Page<>(1, 20, 0));

        shopQueryApi.pageShops(ShopQueryApi.ShopFilter.builder()
                .platform("AMAZON").status(1).pageNo(2).pageSize(50).build());

        ArgumentCaptor<ShopQuery> captor = ArgumentCaptor.forClass(ShopQuery.class);
        verify(shopService).pageShops(captor.capture());
        ShopQuery query = captor.getValue();
        assertEquals("AMAZON", query.getPlatform());
        assertEquals(1, query.getStatus());
        assertEquals(2, query.getPageNo());
        assertEquals(50, query.getPageSize());
    }

    @Test
    void pageShopsNormalizesMissingPaging() {
        when(shopService.pageShops(any())).thenReturn(new Page<>(1, 20, 0));

        shopQueryApi.pageShops(ShopQueryApi.ShopFilter.builder().build());

        ArgumentCaptor<ShopQuery> captor = ArgumentCaptor.forClass(ShopQuery.class);
        verify(shopService).pageShops(captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void pageShopsMapsRowsAndTotalWithoutCredentials() {
        Page<ShopResponse> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(shop()));
        when(shopService.pageShops(any())).thenReturn(page);

        QueryPage<ShopQueryApi.ShopView> result = shopQueryApi.pageShops(
                ShopQueryApi.ShopFilter.builder().build());

        assertEquals(1, result.total());
        ShopQueryApi.ShopView view = result.list().get(0);
        assertEquals(1L, view.id());
        assertEquals("AMAZON", view.platform());
        assertEquals("测试店铺", view.shopName());
        assertEquals("SELLER-1", view.sellerId());
        assertEquals(1, view.status());
        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), view.tokenExpireAt());
    }

    @Test
    void getShopReturnsNullWhenMissing() {
        when(shopService.getShopById(99L)).thenReturn(null);
        assertNull(shopQueryApi.getShop(99L));
    }
}
