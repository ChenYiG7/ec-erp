package com.own.erp.finance.profit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : PeriodSettlementSide 单测(纯值对象,无 mock):费种归类/带符号折算/null=无行 vs 0=合计为零/缺汇率全 NULL。
 *     金额计算必测(docs/07 §10);归类口径 #32 已拍板(④A:SALE 单列 settleSales 进收入差,其余未知费种落 otherFee 不丢值)
 */
class PeriodSettlementSideTest {

    private int cmp(BigDecimal actual, String expected) {
        return actual.compareTo(new BigDecimal(expected));
    }

    @Test
    void missingRateLeavesAllColumnsNull() {
        PeriodSettlementSide side = PeriodSettlementSide.from(
                List.of(new SettlementFeeSum("TRANSFER", new BigDecimal("100.00"))), null);
        assertNull(side.settleSales());
        assertNull(side.settleIncome());
        assertNull(side.settleCommission());
        assertNull(side.fbaFee());
        assertNull(side.otherFee());
    }

    @Test
    void groupsFeeTypesAndConvertsWithSigns() {
        PeriodSettlementSide side = PeriodSettlementSide.from(List.of(
                new SettlementFeeSum("SALE", new BigDecimal("200.00")),
                new SettlementFeeSum("TRANSFER", new BigDecimal("100.00")),
                new SettlementFeeSum("COMMISSION", new BigDecimal("-15.00")),
                new SettlementFeeSum("FBA_FEE", new BigDecimal("-5.00")),
                new SettlementFeeSum("STORAGE", new BigDecimal("-2.00")),
                new SettlementFeeSum("ADVERTISING", new BigDecimal("-3.00")),
                new SettlementFeeSum("OTHER", new BigDecimal("-1.00"))),
                new BigDecimal("7.0"));

        // SALE 200×7=1400(#32 拍板④A 单列进收入差);回款 100×7=700;佣金 −15×7=−105;
        // FBA 系 (−5−2)×7=−49;其他 (−3−1)×7=−28(SALE 不再混入其他费)
        assertEquals(0, cmp(side.settleSales(), "1400.0000"));
        assertEquals(0, cmp(side.settleIncome(), "700.0000"));
        assertEquals(0, cmp(side.settleCommission(), "-105.0000"));
        assertEquals(0, cmp(side.fbaFee(), "-49.0000"));
        assertEquals(0, cmp(side.otherFee(), "-28.0000"));
    }

    @Test
    void unknownFutureFeeTypeFallsIntoOtherNeverDropped() {
        // 新增费种不丢值(缺口纪律),落 otherFee;SALE/TRANSFER/COMMISSION/FBA 系单列,归类 #32 已拍板
        PeriodSettlementSide side = PeriodSettlementSide.from(List.of(
                new SettlementFeeSum("FUTURE_NEW_FEE", new BigDecimal("-0.30"))), new BigDecimal("10"));
        assertEquals(0, cmp(side.otherFee(), "-3.0000"));
        assertNull(side.settleSales());
        assertNull(side.settleIncome());
        assertNull(side.settleCommission());
        assertNull(side.fbaFee());
    }

    @Test
    void absentCategoryIsNullButZeroSumStaysZero() {
        PeriodSettlementSide noFba = PeriodSettlementSide.from(List.of(
                new SettlementFeeSum("TRANSFER", new BigDecimal("10.00"))), BigDecimal.ONE);
        assertNull(noFba.fbaFee(), "无 FBA 行→null(与合计为 0 语义不同)");

        PeriodSettlementSide zeroFba = PeriodSettlementSide.from(List.of(
                new SettlementFeeSum("FBA_FEE", new BigDecimal("1.00")),
                new SettlementFeeSum("STORAGE", new BigDecimal("-1.00"))), BigDecimal.ONE);
        assertEquals(0, cmp(zeroFba.fbaFee(), "0.0000"), "有行合计为 0→0.0000");
    }
}
