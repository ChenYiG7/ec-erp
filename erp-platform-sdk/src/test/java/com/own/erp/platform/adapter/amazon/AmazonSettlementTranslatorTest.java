package com.own.erp.platform.adapter.amazon;

import com.own.erp.platform.PlatformType;
import com.own.erp.platform.unified.UnifiedSettlement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author : chenyi
 * @Date : 2026/9/8
 * @Description : AmazonSettlementTranslator 单测(fixture 为官方文档字段/三段结构推导的自制样例,
 *     非官方脱敏样本——真实报告文件到位后 --force 校准一轮,docs/07 §8):
 *     结算头解析/事件行 fee_type 归一映射/金额带符号原值/EUR 本地化小数/date-only 时间回落/
 *     footer 容忍跳过/非法结构(缺结算头/双结算头/缺必填列/缺金额)全拒禁静默
 */
class AmazonSettlementTranslatorTest {

    private static final String HEADER = String.join("\t",
            "settlement-id", "settlement-start-date", "settlement-end-date", "deposit-date",
            "total-amount", "currency", "transaction-type", "order-id", "shipment-id",
            "amount-type", "amount-description", "amount", "fulfillment-id",
            "posted-date", "posted-date-time", "order-item-code", "sku", "quantity-purchased");

    private static final String REPORT_TSV = HEADER + "\n"
            // 结算头行:settlement-id 列非空,transaction-type 空
            + "4049-4845-94\t2026-08-01T00:00:00+00:00\t2026-08-14T00:00:00+00:00\t2026-08-16T00:00:00+00:00\t"
            + "978.13\tUSD\t\t\t\t\t\t\t\t\t\t\t\t\n"
            // 事件行 1:Order Principal → SALE
            + "\t\t\t\t\t\tOrder\t111-222\t\tPrincipal\t\t29.99\tAFN\t\t2026-08-03T10:00:00+00:00\tC1\tSKU-A\t1\n"
            // 事件行 2:Order Commission → COMMISSION(负值保留)
            + "\t\t\t\t\t\tOrder\t111-222\t\tCommission\t\t-4.50\tAFN\t\t2026-08-03T10:00:00+00:00\tC1\tSKU-A\t\n"
            // 事件行 3:FBA 履约费 → FBA_FEE
            + "\t\t\t\t\t\tOrder\t111-222\t\tFee\tFBAPerUnitFulfillmentFee\t-5.12\tAFN\t\t2026-08-03T10:00:00+00:00\tC1\tSKU-A\t\n"
            // 事件行 4:Refund → REFUND
            + "\t\t\t\t\t\tRefund\t111-333\t\tPrincipal\t\t-29.99\tAFN\t\t2026-08-05T09:00:00+00:00\tC2\tSKU-A\t1\n"
            // 事件行 5:ServiceFee Storage Fee → STORAGE
            + "\t\t\t\t\t\tServiceFee\t\t\tFee\tStorage Fee\t-2.25\t\t\t2026-08-07T00:00:00+00:00\t\t\t\n"
            // 事件行 6:ServiceFee ABA- 广告 → ADVERTISING
            + "\t\t\t\t\t\tServiceFee\t\t\tFee\tABA-AdvertiserFees\t-10.00\t\t\t2026-08-07T00:00:00+00:00\t\t\t\n"
            // 事件行 7:Transfer → TRANSFER(订单列全空)
            + "\t\t\t\t\t\tTransfer\t\t\t\t\t1000.00\t\t\t2026-08-16T00:00:00+00:00\t\t\t\n"
            // 空行 + 官方汇总段(V1 勾稽不依赖,容忍跳过)
            + "\n"
            + "Settlement Total\ttotal-amount\n"
            + "978.13\tUSD\n";

    @Test
    void parsesHeaderEventsAndSkipsFooter() {
        UnifiedSettlement settlement = AmazonSettlementTranslator.translate(REPORT_TSV, 7L, PlatformType.AMAZON);

        assertEquals("4049-4845-94", settlement.getSettlementId());
        assertEquals(7L, settlement.getShopId());
        assertEquals(PlatformType.AMAZON, settlement.getPlatform());
        assertEquals("USD", settlement.getCurrency());
        assertEquals(0, new BigDecimal("978.13").compareTo(settlement.getTotalAmount()));
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), settlement.getPeriodStart());
        assertEquals(Instant.parse("2026-08-14T00:00:00Z"), settlement.getPeriodEnd());
        assertEquals(Instant.parse("2026-08-16T00:00:00Z"), settlement.getDepositDate());

        assertEquals(7, settlement.getLines().size());
        UnifiedSettlement.Line sale = settlement.getLines().get(0);
        assertEquals(UnifiedSettlement.FeeType.SALE, sale.getFeeType());
        assertEquals("111-222", sale.getOrderId());
        assertEquals("C1", sale.getOrderItemId());
        assertEquals("SKU-A", sale.getSku());
        assertEquals(0, new BigDecimal("29.99").compareTo(sale.getAmount()));
        assertEquals(1, sale.getQuantity());
        assertEquals(Instant.parse("2026-08-03T10:00:00Z"), sale.getPostedAt());
        assertEquals("Order", sale.getTransactionType());

        assertEquals(UnifiedSettlement.FeeType.COMMISSION, settlement.getLines().get(1).getFeeType());
        assertEquals(0, new BigDecimal("-4.50").compareTo(settlement.getLines().get(1).getAmount()));
        assertEquals(UnifiedSettlement.FeeType.FBA_FEE, settlement.getLines().get(2).getFeeType());
        assertEquals(UnifiedSettlement.FeeType.REFUND, settlement.getLines().get(3).getFeeType());
        assertEquals(UnifiedSettlement.FeeType.STORAGE, settlement.getLines().get(4).getFeeType());
        assertEquals(UnifiedSettlement.FeeType.ADVERTISING, settlement.getLines().get(5).getFeeType());
        UnifiedSettlement.Line transfer = settlement.getLines().get(6);
        assertEquals(UnifiedSettlement.FeeType.TRANSFER, transfer.getFeeType());
        assertNull(transfer.getOrderId());
        assertEquals(0, new BigDecimal("1000.00").compareTo(transfer.getAmount()));
    }

    @Test
    void eurAmountsUseCommaAsDecimalSeparator() {
        String tsv = HEADER + "\n"
                + "EU-1\t2026-08-01T00:00:00+00:00\t2026-08-14T00:00:00+00:00\t2026-08-16T00:00:00+00:00\t"
                + "1.234,56\tEUR\t\t\t\t\t\t\t\t\t\t\t\t\n"
                + "\t\t\t\t\t\tOrder\t111-EEE\t\tPrincipal\t\t95,00\tMFN\t\t2026-08-03T10:00:00+00:00\tC9\tSKU-E\t1\n";
        UnifiedSettlement settlement = AmazonSettlementTranslator.translate(tsv, 1L, PlatformType.AMAZON);

        assertEquals("EUR", settlement.getCurrency());
        assertEquals(0, new BigDecimal("1234.56").compareTo(settlement.getTotalAmount()));
        assertEquals(0, new BigDecimal("95.00").compareTo(settlement.getLines().get(0).getAmount()));
    }

    @Test
    void dateOnlyPostedDateFallsBackToUtcMidnight() {
        String tsv = HEADER + "\n"
                + "D-1\t2026-08-01T00:00:00+00:00\t2026-08-14T00:00:00+00:00\t2026-08-16T00:00:00+00:00\t"
                + "10.00\tUSD\t\t\t\t\t\t\t\t\t\t\t\t\n"
                + "\t\t\t\t\t\tTransfer\t\t\t\t\t10.00\t\t\t2026-08-16\t\t\t\n";
        UnifiedSettlement settlement = AmazonSettlementTranslator.translate(tsv, 1L, PlatformType.AMAZON);

        assertEquals(Instant.parse("2026-08-16T00:00:00Z"), settlement.getLines().get(0).getPostedAt());
    }

    @Test
    void rejectsMissingRequiredColumnWithActualHeader() {
        String badHeader = HEADER.replace("\ttotal-amount", "");
        String tsv = badHeader + "\n"
                + "X-1\t2026-08-01T00:00:00+00:00\t2026-08-14T00:00:00+00:00\t2026-08-16T00:00:00+00:00\t"
                + "USD\t\t\t\t\t\t\t\t\t\t\t\t\n";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> AmazonSettlementTranslator.translate(tsv, 1L, PlatformType.AMAZON));

        assertTrue(exception.getMessage().contains("total-amount"), exception.getMessage());
    }

    @Test
    void rejectsReportWithoutSettlementHeaderRow() {
        String tsv = HEADER + "\n"
                + "\t\t\t\t\t\tOrder\t111-222\t\tPrincipal\t\t29.99\tAFN\t\t2026-08-03T10:00:00+00:00\tC1\tSKU-A\t1\n";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> AmazonSettlementTranslator.translate(tsv, 1L, PlatformType.AMAZON));

        assertTrue(exception.getMessage().contains("结算头"), exception.getMessage());
    }

    @Test
    void rejectsSecondSettlementHeaderRow() {
        String headerRow = "4049-4845-94\t2026-08-01T00:00:00+00:00\t2026-08-14T00:00:00+00:00\t"
                + "2026-08-16T00:00:00+00:00\t978.13\tUSD\t\t\t\t\t\t\t\t\t\t\t\t\n";
        String tsv = HEADER + "\n" + headerRow + headerRow;

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> AmazonSettlementTranslator.translate(tsv, 1L, PlatformType.AMAZON));

        assertTrue(exception.getMessage().contains("第二个结算头"), exception.getMessage());
    }

    @Test
    void rejectsEventRowWithoutAmount() {
        String tsv = HEADER + "\n"
                + "4049-4845-94\t2026-08-01T00:00:00+00:00\t2026-08-14T00:00:00+00:00\t2026-08-16T00:00:00+00:00\t"
                + "978.13\tUSD\t\t\t\t\t\t\t\t\t\t\t\t\n"
                + "\t\t\t\t\t\tOrder\t111-222\t\tPrincipal\t\t\tAFN\t\t2026-08-03T10:00:00+00:00\tC1\tSKU-A\t1\n";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> AmazonSettlementTranslator.translate(tsv, 1L, PlatformType.AMAZON));

        assertTrue(exception.getMessage().contains("amount"), exception.getMessage());
    }

    @Test
    void rejectsBlankContent() {
        assertThrows(IllegalStateException.class,
                () -> AmazonSettlementTranslator.translate("  ", 1L, PlatformType.AMAZON));
    }
}
