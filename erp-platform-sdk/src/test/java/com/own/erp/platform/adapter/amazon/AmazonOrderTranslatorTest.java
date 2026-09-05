package com.own.erp.platform.adapter.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.platform.unified.UnifiedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : Amazon 报文翻译单测:样例报文取自 SP-API getOrders/getOrderItems 官方模型结构(脱敏),
 *     不用 mock 报文自嗨(docs/07 §8);断言状态机/时间/金额/地址/明细翻译与 raw_json 回存
 */
class AmazonOrderTranslatorTest {

    private JsonNode orderNode;
    private JsonNode itemsNode;

    @BeforeEach
    void loadFixtures() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/amazon/get-order-sample.json")) {
            orderNode = mapper.readTree(in);
        }
        try (InputStream in = getClass().getResourceAsStream("/amazon/get-order-items-sample.json")) {
            itemsNode = mapper.readTree(in);
        }
    }

    @Test
    void translatesOrderFieldsFromRealSample() {
        UnifiedOrder order = AmazonOrderTranslator.translateOrder(orderNode);

        assertEquals("902-3159894-4163816", order.getPlatformOrderId());
        assertEquals(UnifiedOrder.OrderStatus.SHIPPED, order.getStatus());
        assertEquals(UnifiedOrder.FulfillmentChannel.SELF_FULFILL, order.getFulfillmentChannel());
        assertEquals(Instant.parse("2026-08-29T22:18:44Z"), order.getOrderTime());
        assertNull(order.getPayTime());
        assertEquals(new BigDecimal("109.36"), order.getTotalAmount());
        assertEquals("USD", order.getCurrency());
        // 平台地址结构差异抹平:AddressLine1/2 合并
        assertEquals("John Smith", order.getReceiverName());
        assertEquals("1234 Westlake Ave Suite 500", order.getReceiverAddress());
        assertEquals("Seattle", order.getReceiverCity());
        assertEquals("WA", order.getReceiverState());
        assertEquals("98101", order.getReceiverZip());
        assertEquals("US", order.getReceiverCountry());
        assertEquals("Please ship ASAP", order.getBuyerMessage());
        // raw_json 必存原始报文(docs/07 §8)
        assertEquals(orderNode.toString(), order.getRawJson());
    }

    @Test
    void translatesItemsIncludingPriceMissingLine() {
        // 契约:入参为 getOrderItems 响应中的 OrderItems 数组节点(client 从 payload 中取出)
        java.util.List<UnifiedOrder.Item> items = AmazonOrderTranslator.translateItems(itemsNode.path("OrderItems"));

        assertEquals(2, items.size());
        UnifiedOrder.Item first = items.get(0);
        assertEquals("02553626332530-1", first.getPlatformOrderItemId());
        assertEquals("B00EXAMPLE1", first.getPlatformProductId());
        assertEquals("ERP-SKU-001", first.getSellerSku());
        assertEquals("Wireless Earbuds, Black", first.getTitle());
        assertEquals(2, first.getQuantity());
        assertEquals(new BigDecimal("41.00"), first.getUnitPrice());
        assertEquals(UnifiedOrder.RefundStatus.NONE, first.getRefundStatus());
        // 无价格行(如赠品):单价 null,落库侧归零,不阻断整单
        assertNull(items.get(1).getUnitPrice());
        assertEquals(1, items.get(1).getQuantity());
    }

    @Test
    void mapsAllKnownStatuses() {
        java.util.Map<String, UnifiedOrder.OrderStatus> expected = java.util.Map.of(
                "Pending", UnifiedOrder.OrderStatus.WAIT_PAY,
                "Unshipped", UnifiedOrder.OrderStatus.WAIT_SHIP,
                "PartiallyShipped", UnifiedOrder.OrderStatus.SHIPPED,
                "InvoiceUnconfirmed", UnifiedOrder.OrderStatus.SHIPPED,
                "Canceled", UnifiedOrder.OrderStatus.CANCELLED,
                "Unfulfillable", UnifiedOrder.OrderStatus.CLOSED);
        expected.forEach((amazon, unified) -> {
            ((com.fasterxml.jackson.databind.node.ObjectNode) orderNode).put("OrderStatus", amazon);
            assertEquals(unified, AmazonOrderTranslator.translateOrder(orderNode).getStatus(), amazon);
        });
    }

    @Test
    void afnOrderMapsToFbaChannel() {
        ((com.fasterxml.jackson.databind.node.ObjectNode) orderNode).put("FulfillmentChannel", "AFN");
        assertEquals(UnifiedOrder.FulfillmentChannel.FBA, AmazonOrderTranslator.translateOrder(orderNode).getFulfillmentChannel());
    }

    @Test
    void unknownStatusFailsLoudlyInsteadOfSilentGuess() {
        ((com.fasterxml.jackson.databind.node.ObjectNode) orderNode).put("OrderStatus", "SomeNewStatus");
        assertThrows(IllegalArgumentException.class, () -> AmazonOrderTranslator.translateOrder(orderNode));
    }

    @Test
    void missingOrderIdFailsLoudly() {
        ((com.fasterxml.jackson.databind.node.ObjectNode) orderNode).remove("AmazonOrderId");
        assertThrows(IllegalArgumentException.class, () -> AmazonOrderTranslator.translateOrder(orderNode));
    }
}
