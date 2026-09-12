package com.own.erp.finance.response;

import com.own.erp.finance.entity.PlatformFeeRate;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 平台费率表对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进组件即编译期封死
 */
@Builder
public record PlatformFeeRateResponse(

        /** 主键 */
        Long id,

        /** 平台(PlatformType 枚举名) */
        String platform,

        /** 费种:本期仅 COMMISSION 估佣金(对齐 settlement_detail.fee_type 词表;FBA 仓储类无费率不猜) */
        String feeType,

        /** 站点,空=全站点(维度预留:估费 V1 只取全站点行,站点维启用随真实使用评估) */
        String marketplace,

        /** 类目路径,空=全类目(维度预留:估费 V1 只取全类目行) */
        String categoryPath,

        /** 费率(如 0.150000=15%;必须大于0且小于1,Service 校验) */
        BigDecimal rate,

        /** 生效起(含;按下单日回溯,同维取 eff_from 不晚于下单日的最新一条生效行) */
        LocalDate effFrom,

        /** 生效止(含;空=长期有效) */
        LocalDate effTo,

        /** 来源:MANUAL手工维护/CRAWLED抓取(预留扩展,本期仅 MANUAL) */
        String source,

        /** 备注 */
        String remark,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);builder 命名传参防相邻同类型字段错位 */
    public static PlatformFeeRateResponse from(PlatformFeeRate entity) {
        return PlatformFeeRateResponse.builder()
                .id(entity.getId())
                .platform(entity.getPlatform())
                .feeType(entity.getFeeType())
                .marketplace(entity.getMarketplace())
                .categoryPath(entity.getCategoryPath())
                .rate(entity.getRate())
                .effFrom(entity.getEffFrom())
                .effTo(entity.getEffTo())
                .source(entity.getSource())
                .remark(entity.getRemark())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
