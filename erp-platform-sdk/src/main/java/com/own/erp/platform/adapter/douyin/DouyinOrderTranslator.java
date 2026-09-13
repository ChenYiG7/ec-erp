package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店 /order/searchList 报文 → UnifiedOrder 翻译(防腐层核心,docs/07 §8):
 *         平台差异只允许存在于本类;输入 = 单个订单节点(order/searchList 的 order_list 元素,
 *         样例见 test/resources/douyin);raw_json 必存原始报文回溯;
 *         **金额为分**:抖店金额口径为"分",统一换算成"元"(BigDecimal 除以 100,
 *         docs/07 §1 金额红线;比例换算不丢小数,Round half-up 保 2 位元)——**真凭证样本到位后 --force 校准**;
 *         状态枚举/时间秒级/收件人/明细结构按官方 schema 推导,fixture 为推导样例(docs/07 §8)
 */
final class DouyinOrderTranslator {

    private static final int FEN_PER_YUAN = 100;

    private DouyinOrderTranslator() {
    }

    static UnifiedOrder translateOrder(JsonNode orderNode, Long shopId, PlatformType platform) {
        String platformOrderId = textOrNull(orderNode, "order_id");
        if (platformOrderId == null) {
            throw new IllegalArgumentException("抖店订单缺 order_id: " + orderNode);
        }
        JsonNode receiver = orderNode.path("post_receiver");
        return UnifiedOrder.builder()
                .platformOrderId(platformOrderId)
                .shopId(shopId)
                .platform(platform)
                .status(mapStatus(orderNode.path("order_status").asInt(-1), platformOrderId))
                .fulfillmentChannel(UnifiedOrder.FulfillmentChannel.SELF_FULFILL)
                .orderTime(epochSeconds(orderNode, "create_time"))
                .payTime(epochSecondsNullable(orderNode, "pay_time"))
                .shipByTime(dateTimeNullable(orderNode, "post_receiver_time"))
                .currency("CNY")
                .exchangeRate(BigDecimal.ONE)
                .totalAmount(fenToYuan(orderNode, "order_amount"))
                .postageAmount(fenToYuan(orderNode, "post_amount"))
                .discountAmount(fenToYuan(orderNode, "order_promotion_amount"))
                .receiverName(textOrNull(receiver, "name"))
                .receiverPhone(textOrNull(receiver, "phone"))
                .receiverCountry("中国")
                .receiverState(textOrNull(receiver, "province"))
                .receiverCity(textOrNull(receiver, "city"))
                .receiverDistrict(textOrNull(receiver, "town"))
                .receiverAddress(joinReceiverAddress(receiver))
                .receiverZip(textOrNull(receiver, "postal_code"))
                .buyerMessage(textOrNull(orderNode, "buyer_message"))
                .remark(textOrNull(orderNode, "post_tips"))
                .rawJson(orderNode.toString())
                .items(translateItems(orderNode.path("sku_order_list"), shopId, platform))
                .build();
    }

    /** 行明细:sku_order_list 每个元素一行(platformOrderItemId=sku_order_id,商户编码=out_sku_id) */
    private static List<UnifiedOrder.Item> translateItems(JsonNode skuOrderList, Long shopId, PlatformType platform) {
        List<UnifiedOrder.Item> items = new ArrayList<>();
        for (JsonNode sku : skuOrderList) {
            items.add(UnifiedOrder.Item.builder()
                    .platformOrderItemId(textOrNull(sku, "sku_order_id"))
                    .platformProductId(textOrNull(sku, "product_id"))
                    .platformSkuId(textOrNull(sku, "sku_id"))
                    .title(textOrNull(sku, "product_name"))
                    .skuProps(textOrNull(sku, "spec") == null ? textOrNull(sku, "product_spec") : textOrNull(sku, "spec"))
                    .quantity(sku.path("product_count").asInt(0))
                    .unitPrice(fenToYuan(sku, "price"))
                    .sellerSku(textOrNull(sku, "out_sku_id"))
                    .refundStatus(UnifiedOrder.RefundStatus.NONE)
                    .build());
        }
        return items;
    }

    /**
     * 抖店订单状态 → 统一状态机(docs/04):待支付/部分支付→待付款;已支付/备货中→待发货;
     * 部分发货/已发货→已发货;已取消→已取消;已完成→已完成;退款完结(发货前/发货后/收货后)→已关闭
     */
    private static UnifiedOrder.OrderStatus mapStatus(int douyinStatus, String orderId) {
        return switch (douyinStatus) {
            case 1, 103 -> UnifiedOrder.OrderStatus.WAIT_PAY;
            case 105, 2 -> UnifiedOrder.OrderStatus.WAIT_SHIP;
            case 101, 3 -> UnifiedOrder.OrderStatus.SHIPPED;
            case 4 -> UnifiedOrder.OrderStatus.CANCELLED;
            case 5 -> UnifiedOrder.OrderStatus.COMPLETED;
            case 21, 22, 39 -> UnifiedOrder.OrderStatus.CLOSED;
            default -> throw new IllegalArgumentException(
                    "未知抖店订单状态 order_status=" + douyinStatus + ", order_id=" + orderId);
        };
    }

    /** 抖店金额为分 → 元:除以 100 保 2 位(half-up);缺省/非数字返回 null(时间语义不造假) */
    private static BigDecimal fenToYuan(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText()).divide(new BigDecimal(FEN_PER_YUAN), 2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("抖店金额字段解析失败 " + field + "=" + value);
        }
    }

    /** 秒级时间戳 → Instant(0 视为缺失返回 null) */
    private static java.time.Instant epochSeconds(JsonNode node, String field) {
        long seconds = node.path(field).asLong(-1);
        if (seconds < 0) {
            throw new IllegalArgumentException("抖店时间字段缺失 " + field);
        }
        return seconds == 0 ? null : java.time.Instant.ofEpochSecond(seconds);
    }

    private static java.time.Instant epochSecondsNullable(JsonNode node, String field) {
        long seconds = node.path(field).asLong(-1);
        return seconds < 0 ? null : (seconds == 0 ? null : java.time.Instant.ofEpochSecond(seconds));
    }

    /** 时间日期字符串(形如 "2026-09-01 12:00:00")→ Instant;缺省/空返回 null(结构性字段不造假) */
    private static java.time.Instant dateTimeNullable(JsonNode node, String field) {
        String raw = textOrNull(node, field);
        return raw == null ? null : java.time.LocalDateTime
                .parse(raw.replace(' ', 'T'), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
    }

    /** 收件人详细地址拼接(省市区 + 详细地址,平台地址结构差异归 adapter 抹平) */
    private static String joinReceiverAddress(JsonNode receiver) {
        StringBuilder sb = new StringBuilder();
        append(sb, textOrNull(receiver, "province"));
        append(sb, textOrNull(receiver, "city"));
        append(sb, textOrNull(receiver, "town"));
        append(sb, textOrNull(receiver, "detail"));
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}