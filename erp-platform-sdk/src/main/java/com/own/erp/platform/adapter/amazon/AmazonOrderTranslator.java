package com.own.erp.platform.adapter.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.own.erp.platform.unified.UnifiedOrder;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/4
 * @Description : Amazon Orders 报文 → UnifiedOrder 翻译(防腐层核心,docs/07 §8):
 *         平台差异(状态枚举/时区时间/金额字符串/地址结构)只允许存在于本类,业务层只见统一模型;
 *         输入 = SP-API getOrders / getOrderItems 响应节点(样例见 test/resources/amazon);
 *         raw_json 必存原始报文(docs/07 §8,翻译出错可回溯重放);
 *         缺平台单号/未知状态抛 IllegalArgumentException → 上层记 pull_log 失败重试,禁静默吞;
 *         金额:Amazon Amount 为十进制字符串(已是元),BigDecimal 直读,不做最小单位换算(docs/07 §1 金额红线)
 */
final class AmazonOrderTranslator {

    private AmazonOrderTranslator() {
    }

    /** getOrders 响应中的单个订单节点 → UnifiedOrder(明细由 getOrderItems 另行翻译后挂载);builder 装配(docs/07 §1) */
    static UnifiedOrder translateOrder(JsonNode orderNode) {
        String platformOrderId = textOrNull(orderNode, "AmazonOrderId");
        if (platformOrderId == null) {
            throw new IllegalArgumentException("Amazon 订单缺 AmazonOrderId: " + orderNode);
        }
        JsonNode total = orderNode.path("OrderTotal");
        JsonNode address = orderNode.path("ShippingAddress");
        return UnifiedOrder.builder()
                .platformOrderId(platformOrderId)
                .status(mapStatus(textOrNull(orderNode, "OrderStatus")))
                .fulfillmentChannel("AFN".equals(orderNode.path("FulfillmentChannel").asText())
                        ? UnifiedOrder.FulfillmentChannel.FBA : UnifiedOrder.FulfillmentChannel.SELF_FULFILL)
                .orderTime(parseInstant(orderNode, "PurchaseDate"))
                // getOrders 基础报文无独立支付时间字段,置 null(时间语义不造假,排障走 raw_json)
                .payTime(null)
                .shipByTime(parseInstantNullable(orderNode, "LatestShipDate"))
                .totalAmount(decimalOrNull(total.path("Amount")))
                .currency(total.path("CurrencyCode").asText(null))
                .receiverName(textOrNull(address, "Name"))
                .receiverPhone(textOrNull(address, "Phone"))
                .receiverCountry(textOrNull(address, "CountryCode"))
                .receiverState(textOrNull(address, "StateOrRegion"))
                .receiverCity(textOrNull(address, "City"))
                .receiverZip(textOrNull(address, "PostalCode"))
                .receiverAddress(joinAddress(address))
                .buyerMessage(textOrNull(orderNode, "BuyerNote"))
                .rawJson(orderNode.toString())
                .build();
    }

    /** getOrderItems 响应的 OrderItems 节点 → 行明细;退款状态不走 Orders API( finances/refunds,#3 后续),统一 NONE */
    static List<UnifiedOrder.Item> translateItems(JsonNode orderItemsNode) {
        List<UnifiedOrder.Item> items = new ArrayList<>();
        for (JsonNode itemNode : orderItemsNode) {
            items.add(UnifiedOrder.Item.builder()
                    .platformOrderItemId(textOrNull(itemNode, "OrderItemId"))
                    .platformProductId(textOrNull(itemNode, "ASIN"))
                    .platformSkuId(textOrNull(itemNode, "SellerSKU"))
                    .sellerSku(textOrNull(itemNode, "SellerSKU"))
                    .title(textOrNull(itemNode, "Title"))
                    .quantity(itemNode.path("QuantityOrdered").asInt())
                    .unitPrice(decimalOrNull(itemNode.path("ItemPrice").path("Amount")))
                    .refundStatus(UnifiedOrder.RefundStatus.NONE)
                    .build());
        }
        return items;
    }

    /**
     * 统一状态机映射(docs/04):Pending→待付款;Unshipped→待发货;
     * PartiallyShipped/Shipped/InvoiceUnconfirmed→已发货(统一状态机无"部分发货",按主流程推进,raw_json 兜底);
     * Canceled→已取消;Unfulfillable→已关闭
     */
    private static UnifiedOrder.OrderStatus mapStatus(String amazonStatus) {
        if (amazonStatus == null) {
            throw new IllegalArgumentException("Amazon 订单缺 OrderStatus");
        }
        return switch (amazonStatus) {
            case "Pending" -> UnifiedOrder.OrderStatus.WAIT_PAY;
            case "Unshipped" -> UnifiedOrder.OrderStatus.WAIT_SHIP;
            case "PartiallyShipped", "Shipped", "InvoiceUnconfirmed" -> UnifiedOrder.OrderStatus.SHIPPED;
            case "Canceled" -> UnifiedOrder.OrderStatus.CANCELLED;
            case "Unfulfillable" -> UnifiedOrder.OrderStatus.CLOSED;
            default -> throw new IllegalArgumentException("未知 Amazon 订单状态: " + amazonStatus);
        };
    }

    /** SP-API 时间为 ISO8601(Z 或 +00:00),OffsetDateTime 兼容两种;解析失败视报文脏数据抛出 */
    private static java.time.Instant parseInstant(JsonNode node, String field) {
        String raw = textOrNull(node, field);
        if (raw == null) {
            throw new IllegalArgumentException("Amazon 订单缺 " + field);
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Amazon 时间字段解析失败 " + field + "=" + raw, e);
        }
    }

    private static java.time.Instant parseInstantNullable(JsonNode node, String field) {
        return textOrNull(node, field) == null ? null : parseInstant(node, field);
    }

    /** AddressLine1/2/3 合并为详细地址(平台地址结构差异归 adapter 抹平) */
    private static String joinAddress(JsonNode address) {
        List<String> lines = new ArrayList<>();
        for (String field : new String[]{"AddressLine1", "AddressLine2", "AddressLine3"}) {
            String line = textOrNull(address, field);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? null : String.join(" ", lines);
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static java.math.BigDecimal decimalOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : new java.math.BigDecimal(node.asText());
    }
}
