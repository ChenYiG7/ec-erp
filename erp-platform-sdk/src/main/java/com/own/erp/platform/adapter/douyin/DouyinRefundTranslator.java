package com.own.erp.platform.adapter.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedRefund;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/13
 * @Description : 抖店 /afterSale/List 报文 → UnifiedRefund 翻译(防腐层 core,docs/07 §8):
 *         aftersale_type/standard_aftersale_status 按官方枚举映射(见 diff 表),未知状态抛异常(禁静默吞,拉单重试);
 *         金额为分 → 元;fixture=推导样例,--force 校准随真凭证(docs/07 §8);禁业务 if
 */
final class DouyinRefundTranslator {

    private DouyinRefundTranslator() {
    }

    static UnifiedRefund translateRefund(JsonNode node, Long shopId, PlatformType platform) {
        String refundId = textOrNull(node, "aftersale_id");
        if (refundId == null) {
            throw new IllegalArgumentException("抖店售后单缺 aftersale_id: " + node);
        }
        List<UnifiedRefund.Item> items = new ArrayList<>();
        JsonNode itemList = node.path("item_list");
        if (itemList.isArray()) {
            for (JsonNode item : itemList) {
                items.add(UnifiedRefund.Item.builder()
                        .platformSkuId(textOrNull(item, "sku_id"))
                        .sellerSku(textOrNull(item, "out_sku_id"))
                        .quantity(item.path("product_count").asInt(0))
                        .refundAmount(fenToYuanOrNull(item, "refund_amount"))
                        .build());
            }
        }
        return UnifiedRefund.builder()
                .platformRefundId(refundId)
                .platformOrderId(textOrNull(node, "order_id"))
                .shopId(shopId)
                .platform(platform)
                .type(mapType(node.path("aftersale_type").asInt(-1), refundId))
                .status(mapStatus(node.path("standard_aftersale_status").asInt(-1), refundId))
                .currency("CNY")
                .refundAmount(fenToYuanOrNull(node, "refund_amount"))
                .reason(textOrNull(node, "reason"))
                .applyTime(epochSeconds(node, "create_time"))
                .finishTime(epochSecondsNullable(node, "finish_time"))
                .items(items)
                .build();
    }

    /** aftersale_type:0 退货退款 / 1 已发货退款 / 2 未发货退款 → 退款类;3 换货 / 7 补寄 → 各自;6 价保/8 维修未知映射抛异常 */
    private static UnifiedRefund.RefundType mapType(int type, String refundId) {
        return switch (type) {
            case 0 -> UnifiedRefund.RefundType.RETURN_REFUND;
            case 1, 2 -> UnifiedRefund.RefundType.REFUND_ONLY;
            case 3 -> UnifiedRefund.RefundType.EXCHANGE;
            case 7 -> UnifiedRefund.RefundType.RESEND;
            case 6, 8 -> throw new IllegalArgumentException(
                    "抖店售后类型未映射(价保/维修) aftersale_type=" + type + ", aftersale_id=" + refundId);
            default -> throw new IllegalArgumentException(
                    "未知抖店售后类型 aftersale_type=" + type + ", aftersale_id=" + refundId);
        };
    }

    /** standard_aftersale_status:6/7/11 待商家/待买家/待二次同意→处理中;8/13 待商家发货/待收货→待收货;12/14 成功→已完成;27/28/29 拒绝/失败→已拒绝 */
    private static UnifiedRefund.RefundStatus mapStatus(int status, String refundId) {
        return switch (status) {
            case 6, 7, 11 -> UnifiedRefund.RefundStatus.APPLYING;
            case 8, 13 -> UnifiedRefund.RefundStatus.WAIT_RECEIVE;
            case 12, 14 -> UnifiedRefund.RefundStatus.FINISHED;
            case 27, 28, 29 -> UnifiedRefund.RefundStatus.REJECTED;
            default -> throw new IllegalArgumentException(
                    "未知抖店售后状态 standard_aftersale_status=" + status + ", aftersale_id=" + refundId);
        };
    }

    private static BigDecimal fenToYuanOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return new BigDecimal(value.asText()).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private static java.time.Instant epochSeconds(JsonNode node, String field) {
        long seconds = node.path(field).asLong(-1);
        if (seconds < 0) {
            throw new IllegalArgumentException("抖店售后缺时间字段 " + field);
        }
        return seconds == 0 ? null : java.time.Instant.ofEpochSecond(seconds);
    }

    private static java.time.Instant epochSecondsNullable(JsonNode node, String field) {
        long seconds = node.path(field).asLong(-1);
        return seconds < 0 ? null : (seconds == 0 ? null : java.time.Instant.ofEpochSecond(seconds));
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}