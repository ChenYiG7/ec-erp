package com.own.erp.order.service;

import com.own.erp.common.exception.BusinessException;
import com.own.erp.contract.GoodsSkuApi;
import com.own.erp.contract.OrderReviewConsts;
import com.own.erp.contract.ShopQueryApi;
import com.own.erp.order.entity.ShopOrder;
import com.own.erp.order.entity.ShopOrderItem;
import com.own.erp.order.mapper.ShopOrderItemMapper;
import com.own.erp.order.mapper.ShopOrderMapper;
import com.own.erp.order.request.command.ManualOrderItemSaveRequest;
import com.own.erp.order.request.command.ManualOrderSaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : ManualOrderService 单测(#29 订单域补课内销录单,AIR:mock Mapper 与契约,不依赖数据库):
 *     服务端派生(合成单号/平台/状态/金额/审核态)、SKU 必绑校验、单据状态与来源守卫
 */
class ManualOrderServiceTest {

    private ShopOrderMapper shopOrderMapper;
    private ShopOrderItemMapper shopOrderItemMapper;
    private OrderRiskEvaluator orderRiskEvaluator;
    private ShopQueryApi shopQueryApi;
    private GoodsSkuApi goodsSkuApi;
    private ManualOrderService manualOrderService;

    @BeforeEach
    void setUp() {
        shopOrderMapper = mock(ShopOrderMapper.class);
        shopOrderItemMapper = mock(ShopOrderItemMapper.class);
        orderRiskEvaluator = mock(OrderRiskEvaluator.class);
        shopQueryApi = mock(ShopQueryApi.class);
        goodsSkuApi = mock(GoodsSkuApi.class);
        manualOrderService = new ManualOrderService(shopOrderMapper, shopOrderItemMapper,
                orderRiskEvaluator, shopQueryApi, goodsSkuApi);
    }

    @Test
    void createDerivesSyntheticNoPlatformStatusAmountAndReviewState() {
        when(shopQueryApi.getShop(2L)).thenReturn(ShopQueryApi.ShopView.builder()
                .id(2L).platform("TAOBAO").shopName("旗舰店").build());
        when(goodsSkuApi.existsSku(99L)).thenReturn(true);
        when(goodsSkuApi.existsSku(100L)).thenReturn(true);
        when(shopOrderMapper.selectCount(any())).thenReturn(3L);
        // MP insert 由库生成主键:mock 回填,验证明细 orderId 装配
        when(shopOrderMapper.insert(any(ShopOrder.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ShopOrder.class).setId(500L);
            return 1;
        });

        Long id = manualOrderService.create(request());

        assertEquals(500L, id);
        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderMapper).insert(orderCaptor.capture());
        ShopOrder saved = orderCaptor.getValue();
        // 服务端派生:平台取店铺真实平台、状态 WAIT_SHIP、来源 MANUAL、履约自履约
        assertEquals("TAOBAO", saved.getPlatform());
        assertEquals("WAIT_SHIP", saved.getOrderStatus());
        assertEquals(OrderReviewConsts.SOURCE_MANUAL, saved.getOrderSource());
        assertEquals("SELF_FULFILL", saved.getFulfillmentChannel());
        // 合成单号 MAN-{shopId}-{yyyyMMdd}-{4位seq}(count=3 → 0004)
        assertNotNull(saved.getPlatformOrderId());
        assertTrue(saved.getPlatformOrderId().startsWith("MAN-2-"), saved.getPlatformOrderId());
        assertTrue(saved.getPlatformOrderId().endsWith("-0004"), saved.getPlatformOrderId());
        // 金额服务端计算:2×10.50 + 1×5.00 = 26.00;币种缺省 CNY;风控未命中 → 无需审核
        assertEquals(0, saved.getOrderAmount().compareTo(new BigDecimal("26.00")));
        assertEquals(0, saved.getShippingFee().compareTo(BigDecimal.ZERO));
        assertEquals("CNY", saved.getCurrency());
        assertEquals(OrderReviewConsts.REVIEW_NONE, saved.getReviewStatus().intValue());
        // 明细小计逐行计算 + 币种跟随订单头(库 NOT NULL)
        ArgumentCaptor<ShopOrderItem> itemCaptor = ArgumentCaptor.forClass(ShopOrderItem.class);
        verify(shopOrderItemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
        List<ShopOrderItem> items = itemCaptor.getAllValues();
        assertEquals(500L, items.get(0).getOrderId());
        assertEquals(99L, items.get(0).getSkuId());
        assertEquals(0, items.get(0).getItemAmount().compareTo(new BigDecimal("21.00")));
        assertEquals(0, items.get(1).getItemAmount().compareTo(new BigDecimal("5.00")));
        assertEquals("CNY", items.get(1).getCurrency());
    }

    @Test
    void createFlagsReviewPendingWhenRiskKeywordHits() {
        when(shopQueryApi.getShop(2L)).thenReturn(ShopQueryApi.ShopView.builder().id(2L).platform("TAOBAO").build());
        when(goodsSkuApi.existsSku(99L)).thenReturn(true);
        when(goodsSkuApi.existsSku(100L)).thenReturn(true);
        when(shopOrderMapper.selectCount(any())).thenReturn(0L);
        when(orderRiskEvaluator.evaluate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("风控关键词:刷单");

        manualOrderService.create(request());

        ArgumentCaptor<ShopOrder> orderCaptor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderMapper).insert(orderCaptor.capture());
        assertEquals(OrderReviewConsts.REVIEW_PENDING, orderCaptor.getValue().getReviewStatus().intValue());
        assertEquals("风控关键词:刷单", orderCaptor.getValue().getRiskFlag());
    }

    @Test
    void createRejectsMissingShop() {
        when(shopQueryApi.getShop(2L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> manualOrderService.create(request()));
        verify(shopOrderMapper, never()).insert(any(ShopOrder.class));
    }

    @Test
    void createRejectsUnboundSku() {
        when(shopQueryApi.getShop(2L)).thenReturn(ShopQueryApi.ShopView.builder().id(2L).platform("TAOBAO").build());
        when(goodsSkuApi.existsSku(99L)).thenReturn(false);

        // 内销单 sku_id 必绑:SKU 不存在整单拒绝,不落半单
        assertThrows(BusinessException.class, () -> manualOrderService.create(request()));
        verify(shopOrderMapper, never()).insert(any(ShopOrder.class));
    }

    @Test
    void updateRejectsPlatformOrderAndNonWaitShipStatus() {
        ShopOrder platform = new ShopOrder();
        platform.setId(7L);
        platform.setShopId(2L);
        platform.setOrderSource(OrderReviewConsts.SOURCE_PLATFORM);
        when(shopOrderMapper.selectById(7L)).thenReturn(platform);

        assertThrows(BusinessException.class, () -> manualOrderService.update(7L, request()));

        ShopOrder shipped = new ShopOrder();
        shipped.setId(8L);
        shipped.setShopId(2L);
        shipped.setOrderSource(OrderReviewConsts.SOURCE_MANUAL);
        shipped.setOrderStatus("SHIPPED");
        when(shopOrderMapper.selectById(8L)).thenReturn(shipped);

        assertThrows(BusinessException.class, () -> manualOrderService.update(8L, request()));
        verify(shopOrderMapper, never()).updateById(any(ShopOrder.class));
    }

    private ManualOrderSaveRequest request() {
        return ManualOrderSaveRequest.builder()
                .shopId(2L)
                .receiverName("张三")
                .receiverPhone("13800000000")
                .receiverCountry("CN")
                .receiverCity("宁波")
                .receiverAddress("某某路 1 号")
                .receiverZip("315000")
                .items(List.of(
                        ManualOrderItemSaveRequest.builder()
                                .skuId(99L).productName("无线耳机").quantity(2).unitPrice(new BigDecimal("10.50")).build(),
                        ManualOrderItemSaveRequest.builder()
                                .skuId(100L).quantity(1).unitPrice(new BigDecimal("5")).build()))
                .build();
    }
}
