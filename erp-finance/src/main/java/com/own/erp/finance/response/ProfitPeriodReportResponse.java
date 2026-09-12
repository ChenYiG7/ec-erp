package com.own.erp.finance.response;

import com.own.erp.finance.entity.ProfitPeriodReport;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 周期利润报告对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进组件即编译期封死
 */
@Builder
public record ProfitPeriodReportResponse(

        /** 主键 */
        Long id,

        /** 店铺ID(shop.id) */
        Long shopId,

        /** 关联结算报告ID(settlement_report.id) */
        Long settlementId,

        /** 周期起(=结算报告 period_start) */
        LocalDateTime periodStart,

        /** 周期止(=结算报告 period_end) */
        LocalDateTime periodEnd,

        /** 结算原生币种(ISO 4217;按原币聚合后统一折算 CNY) */
        String currency,

        /** 折算CNY汇率快照(周期止回溯 resolveRate 冻结,1 currency=rate CNY,CNY=1;无报价NULL禁猜) */
        BigDecimal rateUsed,

        /** 缺汇率标记:0否 1是(1时CNY列留NULL,缺口计数不静默归零) */
        Integer rateMissing,

        /** 订单口径收入(CNY;周期窗内已支付态订单行售价合计) */
        BigDecimal orderIncome,

        /** 结算口径回款(CNY;TRANSFER 行折算,报告原符号) */
        BigDecimal settleIncome,

        /** 结算侧佣金(CNY;COMMISSION 行带符号合计,佣金为负) */
        BigDecimal settleCommission,

        /** FBA系费用(CNY;FBA_FEE/STORAGE 行带符号合计,费用为负) */
        BigDecimal fbaFee,

        /** 其他费用(CNY;REFUND/ADVERTISING/OTHER等费种带符号合计,SALE单列进收入差/TRANSFER不进差值,#32 拍板④A) */
        BigDecimal otherFee,

        /** 订单口径佣金(CNY;周期窗内订单行实际佣金+费率预估佣金合计) */
        BigDecimal orderCommission,

        /** 订单口径利润(CNY;周期窗内订单行利润合计,预估参与时带ESTIMATED语义) */
        BigDecimal orderProfit,

        /** 收入校差(CNY;结算SALE行合计-订单收入,同号相减正=结算侧多,超0.01容差置diff_flag,#32 拍板④A) */
        BigDecimal diffIncome,

        /** 佣金校差(CNY;结算佣金-订单归集佣金,同号相减正=结算侧多,超0.01容差置diff_flag) */
        BigDecimal diffCommission,

        /** 校差超容差标记:0勾稽平 1有差异(容差0.01本位币,同退款勾稽防尾差) */
        Integer diffFlag,

        /** 校差说明(差异项/跨期口径与缺口计数,人工复核入口) */
        String diffRemark,

        /** 状态:OK勾稽平/DIFF有差异/RATE_MISSING缺汇率(三态RATE_MISSING优先,#32 拍板③) */
        String status,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static ProfitPeriodReportResponse from(ProfitPeriodReport entity) {
        return ProfitPeriodReportResponse.builder()
                .id(entity.getId())
                .shopId(entity.getShopId())
                .settlementId(entity.getSettlementId())
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .currency(entity.getCurrency())
                .rateUsed(entity.getRateUsed())
                .rateMissing(entity.getRateMissing())
                .orderIncome(entity.getOrderIncome())
                .settleIncome(entity.getSettleIncome())
                .settleCommission(entity.getSettleCommission())
                .fbaFee(entity.getFbaFee())
                .otherFee(entity.getOtherFee())
                .orderCommission(entity.getOrderCommission())
                .orderProfit(entity.getOrderProfit())
                .diffIncome(entity.getDiffIncome())
                .diffCommission(entity.getDiffCommission())
                .diffFlag(entity.getDiffFlag())
                .diffRemark(entity.getDiffRemark())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
