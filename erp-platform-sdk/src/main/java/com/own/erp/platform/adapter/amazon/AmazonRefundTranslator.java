package com.own.erp.platform.adapter.amazon;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedRefund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : Amazon Finances 退款事件 → UnifiedRefund 翻译器(#3 联调预备骨架,防腐层内平台差异唯一容身处):
 *         - 字段名取自官方 finances-api-model 的 RefundEvent(OrderId/PostedDate/Sku/QuantityPurchased/
 *           PrincipalAmount 等),**真实报文样例到位后须 --force 校准一轮**(docs/07 §8 官方样例单测纪律,
 *           现有单测 fixture 为 schema 推导的自制样例,非官方脱敏样本);
 *         - 状态映射拍板:Finances 退款事件即退款事实(无申请中/已拒绝等状态流),PostedDate 即退款时间
 *           → status=FINISHED + type=REFUND_ONLY → erp-aftersale saveUnifiedRefund 按"FINISHED 退款类→REFUNDED"
 *           分流落终态回传,条件推进未决态单(契合 #12"仅平台终态回传条件推进"拍板,退款退货明细走人工收退件);
 *         - **platformRefundId 拍板(2026-09-06)**:Finances 退款事件无原生唯一 ID,组合幂等键
 *           `{OrderId}|{Sku}|{PostedDate}`——同事件重拉 upsert 冲突即更新不重复建单(铁律 5);
 *           同订单同 SKU 同秒多笔退款的概率随真凭证观测,真冲突时演进组合键维度并登记 TODO.md #3;
 *         - 金额:refundAmount 取 PrincipalAmount 绝对值(Finances 退款记账为负数,负号是方向不是金额),
 *           币种随该金额 CurrencyCode;佣金/GiftWrap 等分项不对账(财务勾稽三期 settlement,TODO.md #12);
 *         - 核心字段缺失(OrderId/PostedDate)抛异常禁静默(docs/07 §8)
 */
final class AmazonRefundTranslator {

    private AmazonRefundTranslator() {
    }

    /** 单条退款事件 → UnifiedRefund(RefundEvent 为条目级:一事件 = 一订单明细行的退款) */
    static UnifiedRefund translateRefund(JsonNode event, Long shopId, PlatformType platform) {
        String orderId = required(event, "OrderId");
        String postedDate = required(event, "PostedDate");
        Instant posted = Instant.parse(postedDate);
        JsonNode principal = event.path("PrincipalAmount");
        BigDecimal refundAmount = principal.path("Amount").asText(null) == null
                ? null : new BigDecimal(principal.path("Amount").asText()).abs();
        String sku = event.path("Sku").asText(null);
        int quantity = Math.abs(event.path("QuantityPurchased").asInt(0));

        return UnifiedRefund.builder()
                .platformRefundId(orderId + "|" + StrUtil.nullToEmpty(sku) + "|" + postedDate)
                .platformOrderId(orderId)
                .shopId(shopId)
                .platform(platform)
                .type(UnifiedRefund.RefundType.REFUND_ONLY)
                .status(UnifiedRefund.RefundStatus.FINISHED)
                .currency(principal.path("CurrencyCode").asText(null))
                .refundAmount(refundAmount)
                .applyTime(posted)
                .finishTime(posted)
                .items(List.of(UnifiedRefund.Item.builder()
                        .sellerSku(sku)
                        .quantity(quantity)
                        .refundAmount(refundAmount)
                        .build()))
                .build();
    }

    /** 必填字段缺失即拒(禁静默,docs/07 §8):上游拉单记 pull_log 走连续失败告警 */
    private static String required(JsonNode event, String field) {
        String value = event.path(field).asText(null);
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException("RefundEvent 缺必填字段 " + field + ",拒绝静默落库");
        }
        return value;
    }
}
