package com.own.erp.finance.profit;

import com.own.erp.platform.unified.UnifiedSettlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 周期利润结算侧聚合值(#19 三口径第二层):单报告 settlement_detail 分费种原币合计折算 CNY 后的五列。
 *     金额报告原值带符号——SALE(订单侧收入流)/回款(TRANSFER)为正,佣金/FBA 系费用为负;缺汇率时五列全 null(缺口纪律,不猜汇率)。
 *     费种归类(#32 拍板 2026-09-12):SALE 单列进收入差(同口径对齐订单侧收入,拍板④A)、TRANSFER→回款(打款事实,
 *     不进差值)、COMMISSION→佣金、FBA_FEE+STORAGE→FBA 系、其余费种(REFUND/ADVERTISING/OTHER 及未来新增类型)→其他费用(不丢值)
 */
public record PeriodSettlementSide(

        /** 结算口径订单侧收入流(CNY;SALE 行带符号合计,正;收入差=本列-订单收入,#32 拍板④A) */
        BigDecimal settleSales,

        /** 结算口径回款(CNY;TRANSFER 行带符号合计,正;打款事实存列不进差值) */
        BigDecimal settleIncome,

        /** 结算侧佣金(CNY;COMMISSION 行带符号合计,负) */
        BigDecimal settleCommission,

        /** FBA 系费用(CNY;FBA_FEE+STORAGE 带符号合计,负) */
        BigDecimal fbaFee,

        /** 其他费用(CNY;SALE/TRANSFER/COMMISSION/FBA 系之外全部费种带符号合计) */
        BigDecimal otherFee
) {

    /** FBA 系费种(履约+仓储) */
    private static final Set<String> FBA_FAMILY = Set.of(
            UnifiedSettlement.FeeType.FBA_FEE.name(), UnifiedSettlement.FeeType.STORAGE.name());
    /** 已单列费种(其余一律落 otherFee,未来新增费种不丢值) */
    private static final Set<String> EXPLICIT_TYPES = Set.of(
            UnifiedSettlement.FeeType.SALE.name(), UnifiedSettlement.FeeType.TRANSFER.name(),
            UnifiedSettlement.FeeType.COMMISSION.name(),
            UnifiedSettlement.FeeType.FBA_FEE.name(), UnifiedSettlement.FeeType.STORAGE.name());

    /** 分费种原币合计 → CNY 五列;rate 为 null(缺汇率)时五列全 null,不猜不折 */
    public static PeriodSettlementSide from(List<SettlementFeeSum> feeSums, BigDecimal rate) {
        if (rate == null) {
            return new PeriodSettlementSide(null, null, null, null, null);
        }
        Map<String, BigDecimal> sumByType = feeSums.stream()
                .collect(Collectors.toMap(SettlementFeeSum::feeType, SettlementFeeSum::amount, BigDecimal::add));
        BigDecimal fbaOriginal = sumByType.containsKey(UnifiedSettlement.FeeType.FBA_FEE.name())
                || sumByType.containsKey(UnifiedSettlement.FeeType.STORAGE.name())
                ? sumByType.getOrDefault(UnifiedSettlement.FeeType.FBA_FEE.name(), BigDecimal.ZERO)
                        .add(sumByType.getOrDefault(UnifiedSettlement.FeeType.STORAGE.name(), BigDecimal.ZERO))
                : null;
        BigDecimal otherOriginal = null;
        for (SettlementFeeSum row : feeSums) {
            if (!EXPLICIT_TYPES.contains(row.feeType())) {
                otherOriginal = otherOriginal == null ? row.amount() : otherOriginal.add(row.amount());
            }
        }
        return new PeriodSettlementSide(
                toCny(sumByType.get(UnifiedSettlement.FeeType.SALE.name()), rate),
                toCny(sumByType.get(UnifiedSettlement.FeeType.TRANSFER.name()), rate),
                toCny(sumByType.get(UnifiedSettlement.FeeType.COMMISSION.name()), rate),
                toCny(fbaOriginal, rate),
                toCny(otherOriginal, rate));
    }

    /** 原币→CNY 统一标度 DECIMAL(12,4),HALF_UP;null(该费种无行)透传不替 0,0 与缺值语义不同 */
    private static BigDecimal toCny(BigDecimal amountOriginal, BigDecimal rate) {
        return amountOriginal == null ? null
                : amountOriginal.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }
}
