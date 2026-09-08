package com.own.erp.ai.tools;

import com.own.erp.contract.AftersaleQueryApi;
import com.own.erp.contract.DeliveryQueryApi;
import com.own.erp.contract.GoodsQueryApi;
import com.own.erp.contract.InventoryQueryApi;
import com.own.erp.contract.OrderQueryApi;
import com.own.erp.contract.PurchaseQueryApi;
import com.own.erp.contract.QueryPage;
import com.own.erp.contract.ShopQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/7
 * @Description : tools 分页参数缺省回归(2026-09-07 真模型联调炸出):模型不传可选分页参数时
 *     基础类型 int 拆箱 NPE——四类工具统一改 Integer + 空值回退 0,归一收口各 Filter record 的 page()/size()
 */
class ToolsPagingDefaultsTest {

    @Test
    void inventoryNullPagingDefaultsToPageOneSizeTwenty() {
        InventoryQueryApi api = mock(InventoryQueryApi.class);
        when(api.pageInventory(any())).thenReturn(QueryPage.of(List.of(), 0));
        new InventoryTools(api).queryInventory(1L, 1L, null, null);

        ArgumentCaptor<InventoryQueryApi.InventoryFilter> captor =
                ArgumentCaptor.forClass(InventoryQueryApi.InventoryFilter.class);
        verify(api).pageInventory(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void orderNullPagingDefaultsToPageOneSizeTwenty() {
        OrderQueryApi api = mock(OrderQueryApi.class);
        when(api.pageOrders(any())).thenReturn(QueryPage.of(List.of(), 0));
        new OrderTools(api).listOrders(null, null, null, null, null);

        ArgumentCaptor<OrderQueryApi.OrderFilter> captor =
                ArgumentCaptor.forClass(OrderQueryApi.OrderFilter.class);
        verify(api).pageOrders(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void goodsNullPagingDefaultsToPageOneSizeTwenty() {
        GoodsQueryApi api = mock(GoodsQueryApi.class);
        when(api.pageProducts(any())).thenReturn(QueryPage.of(List.of(), 0));
        new GoodsTools(api).searchProducts(null, null, null, null);

        ArgumentCaptor<GoodsQueryApi.ProductFilter> captor =
                ArgumentCaptor.forClass(GoodsQueryApi.ProductFilter.class);
        verify(api).pageProducts(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void aftersaleNullPagingDefaultsToPageOneSizeTwenty() {
        AftersaleQueryApi api = mock(AftersaleQueryApi.class);
        when(api.pageAftersales(any())).thenReturn(QueryPage.of(List.of(), 0));
        new AftersaleTools(api).listAftersales(null, null, null, null, null, null);

        ArgumentCaptor<AftersaleQueryApi.AftersaleFilter> captor =
                ArgumentCaptor.forClass(AftersaleQueryApi.AftersaleFilter.class);
        verify(api).pageAftersales(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void shopNullPagingDefaultsToPageOneSizeTwenty() {
        ShopQueryApi api = mock(ShopQueryApi.class);
        when(api.pageShops(any())).thenReturn(QueryPage.of(List.of(), 0));
        new ShopTools(api).listShops(null, null, null, null);

        ArgumentCaptor<ShopQueryApi.ShopFilter> captor =
                ArgumentCaptor.forClass(ShopQueryApi.ShopFilter.class);
        verify(api).pageShops(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void purchaseNullPagingDefaultsToPageOneSizeTwenty() {
        PurchaseQueryApi api = mock(PurchaseQueryApi.class);
        when(api.pagePurchaseOrders(any())).thenReturn(QueryPage.of(List.of(), 0));
        new PurchaseTools(api).listPurchaseOrders(null, null, null, null, null);

        ArgumentCaptor<PurchaseQueryApi.PurchaseOrderFilter> captor =
                ArgumentCaptor.forClass(PurchaseQueryApi.PurchaseOrderFilter.class);
        verify(api).pagePurchaseOrders(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }

    @Test
    void deliveryNullPagingDefaultsToPageOneSizeTwenty() {
        DeliveryQueryApi api = mock(DeliveryQueryApi.class);
        when(api.pageDeliveries(any())).thenReturn(QueryPage.of(List.of(), 0));
        new DeliveryTools(api).listDeliveries(null, null, null, null, null, null);

        ArgumentCaptor<DeliveryQueryApi.DeliveryFilter> captor =
                ArgumentCaptor.forClass(DeliveryQueryApi.DeliveryFilter.class);
        verify(api).pageDeliveries(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(20, captor.getValue().size());
    }
}
