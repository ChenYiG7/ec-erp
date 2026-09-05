package com.own.erp.order.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.own.erp.common.exception.BusinessException;
import com.own.erp.order.entity.ShopOrder;
import com.own.erp.order.entity.ShopOrderItem;
import com.own.erp.order.mapper.ShopOrderItemMapper;
import com.own.erp.order.mapper.ShopOrderMapper;
import com.own.erp.order.request.query.ShopOrderQuery;
import com.own.erp.order.response.ShopOrderResponse;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : ShopOrderService 单测(AIR:mock Mapper,不依赖数据库);
 *     覆盖 #4 拉单落库:幂等 upsert 主流程、明细翻译与金额计算、必填校验(docs/07 §10 必测清单)
 */
class ShopOrderServiceTest {

    private ShopOrderMapper shopOrderMapper;
    private ShopOrderItemMapper shopOrderItemMapper;
    private ShopOrderService shopOrderService;

    @BeforeEach
    void setUp() {
        shopOrderMapper = mock(ShopOrderMapper.class);
        shopOrderItemMapper = mock(ShopOrderItemMapper.class);
        shopOrderService = new ShopOrderService(shopOrderMapper, shopOrderItemMapper);
    }

    @Test
    void getByIdMapsToResponseAndReturnsNullWhenMissing() {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setId(1L);
        when(shopOrderMapper.selectById(1L)).thenReturn(shopOrder);
        assertEquals(1L, shopOrderService.getById(1L).id());
        assertNull(shopOrderService.getById(404L));
    }

    @Test
    void getByIdBringsItemsAlong() {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setId(1L);
        when(shopOrderMapper.selectById(1L)).thenReturn(shopOrder);
        ShopOrderItem item = new ShopOrderItem();
        item.setId(11L);
        item.setOrderId(1L);
        when(shopOrderItemMapper.selectList(any())).thenReturn(List.of(item));

        ShopOrderResponse response = shopOrderService.getById(1L);

        assertEquals(1, response.items().size());
        assertEquals(11L, response.items().get(0).id());
    }

    @Test
    void pageMapsRecordsToResponse() {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setId(2L);
        Page<ShopOrder> page = new Page<>(1, 10);
        page.setRecords(List.of(shopOrder));
        doReturn(page).when(shopOrderMapper).selectPage(any(), any());
        assertEquals(2L, shopOrderService.page(new ShopOrderQuery()).getRecords().get(0).id());
    }

    @Test
    void saveUnifiedOrderUpsertsTranslatesSkuAndComputesItemAmount() {
        when(shopOrderMapper.selectOne(any())).thenReturn(savedOrder(100L));

        UnifiedOrder order = baseOrder();
        UnifiedOrder.Item bound = new UnifiedOrder.Item();
        bound.setSellerSku("SKU-A");
        bound.setTitle("无线耳机");
        bound.setQuantity(2);
        bound.setUnitPrice(new BigDecimal("10.50"));
        UnifiedOrder.Item unbound = new UnifiedOrder.Item();
        unbound.setSellerSku("SKU-B");
        unbound.setTitle("未绑定商品");
        unbound.setQuantity(1);
        order.setItems(List.of(bound, unbound));

        Long id = shopOrderService.saveUnifiedOrder(1L, order, Map.of("SKU-A", 99L));

        assertEquals(100L, id);
        // 主表 upsert:平台/状态/时间落库,缺省汇率=1、缺省履约渠道=SELF_FULFILL、缺省金额归零
        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderMapper).upsert(orderCaptor.capture());
        ShopOrder saved = orderCaptor.getValue();
        assertEquals(1L, saved.getShopId());
        assertEquals("AMAZON", saved.getPlatform());
        assertEquals("P-001", saved.getPlatformOrderId());
        assertEquals("WAIT_SHIP", saved.getOrderStatus());
        assertEquals("SELF_FULFILL", saved.getFulfillmentChannel());
        // Instant(02:00Z)→ 库内 Asia/Shanghai 会话时区 10:00
        assertEquals(LocalDateTime.of(2026, 9, 4, 10, 0), saved.getOrderTime());
        assertEquals("USD", saved.getCurrency());
        assertEquals(0, saved.getExchangeRate().compareTo(BigDecimal.ONE));
        assertEquals(new BigDecimal("30.00"), saved.getOrderAmount());
        assertEquals(new BigDecimal("5.00"), saved.getShippingFee());
        assertEquals(new BigDecimal("2.00"), saved.getDiscountAmount());
        // 明细先删后插(状态回传可能改行)
        verify(shopOrderItemMapper).delete(any());
        ArgumentCaptor<ShopOrderItem> itemCaptor = ArgumentCaptor.forClass(ShopOrderItem.class);
        verify(shopOrderItemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        List<ShopOrderItem> items = itemCaptor.getAllValues();
        // 已绑定 seller_sku → sku_id 回填;未绑定保持 NULL(订单照常入库)
        assertEquals(99L, items.get(0).getSkuId());
        assertNull(items.get(1).getSkuId());
        assertEquals("SKU-A", items.get(0).getPlatformSku());
        assertEquals("无线耳机", items.get(0).getProductName());
        // 小计 = 单价 × 数量(金额计算必测)
        assertEquals(0, items.get(0).getItemAmount().compareTo(new BigDecimal("21.00")));
        // 单价缺失归零,金额 0×1=0,币种跟随订单头
        assertEquals(0, items.get(1).getUnitPrice().compareTo(BigDecimal.ZERO));
        assertEquals(0, items.get(1).getItemAmount().compareTo(BigDecimal.ZERO));
        assertEquals("USD", items.get(1).getCurrency());
    }

    @Test
    void saveUnifiedOrderRejectsMissingIdempotentKey() {
        UnifiedOrder order = baseOrder();
        order.setPlatformOrderId(" ");

        assertThrows(BusinessException.class, () -> shopOrderService.saveUnifiedOrder(1L, order, Map.of()));
        verifyNoInteractions(shopOrderMapper, shopOrderItemMapper);
    }

    @Test
    void saveUnifiedOrderRejectsMissingRequiredColumns() {
        UnifiedOrder order = baseOrder();
        order.setStatus(null);

        assertThrows(BusinessException.class, () -> shopOrderService.saveUnifiedOrder(1L, order, Map.of()));
        verify(shopOrderMapper, never()).upsert(any());
    }

    @Test
    void saveUnifiedOrderRejectsMissingItemQuantity() {
        when(shopOrderMapper.selectOne(any())).thenReturn(savedOrder(100L));
        UnifiedOrder order = baseOrder();
        UnifiedOrder.Item bad = new UnifiedOrder.Item();
        bad.setSellerSku("SKU-A");
        bad.setQuantity(null);
        order.setItems(List.of(bad));

        // 库 quantity NOT NULL:静默补 0 会污染对账,宁可整单失败由拉单侧重试
        assertThrows(BusinessException.class, () -> shopOrderService.saveUnifiedOrder(1L, order, Map.of()));
        verify(shopOrderItemMapper, never()).insert(any(ShopOrderItem.class));
    }

    @Test
    void saveUnifiedOrderRejectsWhenRowMissingAfterUpsert() {
        when(shopOrderMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> shopOrderService.saveUnifiedOrder(1L, baseOrder(), Map.of()));
        verify(shopOrderItemMapper, never()).delete(any());
    }

    private ShopOrder savedOrder(Long id) {
        ShopOrder saved = new ShopOrder();
        saved.setId(id);
        return saved;
    }

    @Test
    void casOrderStatusHitsOnlyWhenFromStatusMatches() {
        when(shopOrderMapper.casOrderStatus(1L, "WAIT_SHIP", "SHIPPED")).thenReturn(1);
        when(shopOrderMapper.casOrderStatus(1L, "COMPLETED", "SHIPPED")).thenReturn(0);

        assertEquals(true, shopOrderService.casOrderStatus(1L, "WAIT_SHIP", "SHIPPED"));
        assertEquals(false, shopOrderService.casOrderStatus(1L, "COMPLETED", "SHIPPED"));
        assertEquals(false, shopOrderService.casOrderStatus(null, "WAIT_SHIP", "SHIPPED"));
        verify(shopOrderMapper, never()).casOrderStatus(null, "WAIT_SHIP", "SHIPPED");
    }

    private UnifiedOrder baseOrder() {
        UnifiedOrder order = new UnifiedOrder();
        order.setPlatformOrderId("P-001");
        order.setShopId(1L);
        order.setPlatform(PlatformType.AMAZON);
        order.setStatus(UnifiedOrder.OrderStatus.WAIT_SHIP);
        order.setOrderTime(Instant.parse("2026-09-04T02:00:00Z"));
        order.setCurrency("USD");
        order.setTotalAmount(new BigDecimal("30.00"));
        order.setPostageAmount(new BigDecimal("5.00"));
        order.setDiscountAmount(new BigDecimal("2.00"));
        order.setRawJson("{\"id\":\"P-001\"}");
        return order;
    }
}
