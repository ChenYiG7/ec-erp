package com.own.erp.finance.response;

import com.own.erp.finance.entity.FirstLegAlloc;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程运费分摊结果行对外结构(路线 B 期间费用行,利润第三层聚合源);
 *     skuCode 由 Service 经 GoodsQueryApi 契约批量回填
 */
@Builder
public record FirstLegAllocResponse(

        /** 主键 */
        Long id,

        /** 头程单ID */
        Long shipmentId,

        /** SKU ID */
        Long skuId,

        /** SKU 编码(契约批量回填) */
        String skuCode,

        /** 分摊头程运费(CNY) */
        BigDecimal allocAmount,

        /** 分摊基数快照(QTY=件数/WEIGHT=总重g/AMOUNT=金额CNY) */
        BigDecimal allocBase,

        /** 实际生效策略:QTY/WEIGHT/AMOUNT(降级后可能与主单策略不同) */
        String strategy

) {

    /** 实体 → Response(skuCode 缺省由调用方补) */
    public static FirstLegAllocResponse from(FirstLegAlloc entity) {
        return FirstLegAllocResponse.builder()
                .id(entity.getId())
                .shipmentId(entity.getShipmentId())
                .skuId(entity.getSkuId())
                .allocAmount(entity.getAllocAmount())
                .allocBase(entity.getAllocBase())
                .strategy(entity.getStrategy())
                .build();
    }
}
