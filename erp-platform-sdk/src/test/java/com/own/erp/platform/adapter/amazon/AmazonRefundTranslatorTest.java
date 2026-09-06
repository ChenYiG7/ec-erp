package com.own.erp.platform.adapter.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedRefund;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : Amazon 退款翻译器单测:fixture 字段名取自官方 finances-api-model 的 RefundEvent schema
 *     推导(self-made 样例,非官方脱敏报文),真凭证样本到位后 --force 校准(docs/07 §8);
 *     断言:组合幂等键/终态映射(FINISHED+REFUND_ONLY → #12 分流 REFUNDED)/金额取绝对值/必填缺失即拒
 */
class AmazonRefundTranslatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void translatesRefundEventWithCompositeIdempotentKey() throws Exception {
        JsonNode event = mapper.readTree("""
                {"SellerOrderId":"MY-ORDER-1","OrderId":"902-3159894-4163816",
                 "PostedDate":"2026-08-30T00:05:00Z","Sku":"ERP-SKU-001","QuantityPurchased":-1,
                 "PrincipalAmount":{"CurrencyCode":"USD","Amount":"-41.00"},
                 "CommissionAmount":{"CurrencyCode":"USD","Amount":"-6.15"}}""");

        UnifiedRefund refund = AmazonRefundTranslator.translateRefund(event, 7L, PlatformType.AMAZON);

        assertEquals("902-3159894-4163816|ERP-SKU-001|2026-08-30T00:05:00Z", refund.getPlatformRefundId());
        assertEquals("902-3159894-4163816", refund.getPlatformOrderId());
        assertEquals(7L, refund.getShopId());
        assertEquals(PlatformType.AMAZON, refund.getPlatform());
        // 无状态流的退款事实 → FINISHED + REFUND_ONLY(saveUnifiedRefund 分流 REFUNDED 终态回传)
        assertEquals(UnifiedRefund.RefundStatus.FINISHED, refund.getStatus());
        assertEquals(UnifiedRefund.RefundType.REFUND_ONLY, refund.getType());
        // 退款记账为负数,金额取绝对值;币种随该金额
        assertEquals(0, refund.getRefundAmount().compareTo(new java.math.BigDecimal("41.00")));
        assertEquals("USD", refund.getCurrency());
        // PostedDate 即退款事实时间(申请/完成同源)
        assertEquals(refund.getApplyTime(), refund.getFinishTime());
        assertEquals(1, refund.getItems().size());
        assertEquals("ERP-SKU-001", refund.getItems().get(0).getSellerSku());
        assertEquals(1, refund.getItems().get(0).getQuantity());
        assertEquals(0, refund.getItems().get(0).getRefundAmount()
                .compareTo(new java.math.BigDecimal("41.00")));
    }

    @Test
    void missingSkuStillBuildsStableKeyButMissingOrderIdRejected() throws Exception {
        // Sku 可空(整单退款事件可能无 SKU):组合键以空段占位保持稳定
        JsonNode noSku = mapper.readTree("""
                {"OrderId":"902-3159894-4163816","PostedDate":"2026-08-30T00:05:00Z",
                 "PrincipalAmount":{"CurrencyCode":"USD","Amount":"-41.00"}}""");
        UnifiedRefund refund = AmazonRefundTranslator.translateRefund(noSku, 7L, PlatformType.AMAZON);
        assertEquals("902-3159894-4163816||2026-08-30T00:05:00Z", refund.getPlatformRefundId());

        // 必填缺失禁静默(docs/07 §8)
        JsonNode noOrderId = mapper.readTree("{\"PostedDate\":\"2026-08-30T00:05:00Z\"}");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AmazonRefundTranslator.translateRefund(noOrderId, 7L, PlatformType.AMAZON));
        assertTrue(e.getMessage().contains("OrderId"), e.getMessage());

        JsonNode noPostedDate = mapper.readTree("{\"OrderId\":\"902-3159894-4163816\"}");
        assertTrue(assertThrows(IllegalStateException.class,
                () -> AmazonRefundTranslator.translateRefund(noPostedDate, 7L, PlatformType.AMAZON))
                .getMessage().contains("PostedDate"));
    }

    @Test
    void absentAmountAndQuantityStayNullInsteadOfZero() throws Exception {
        // 金额缺列 = 平台无数据,置 null 禁静默归零
        JsonNode minimal = mapper.readTree("""
                {"OrderId":"111-2222222-3333333","PostedDate":"2026-08-30T00:06:00Z"}""");

        UnifiedRefund refund = AmazonRefundTranslator.translateRefund(minimal, 7L, PlatformType.AMAZON);

        assertNull(refund.getRefundAmount());
        assertNull(refund.getCurrency());
        assertEquals(0, refund.getItems().get(0).getQuantity());
    }
}
