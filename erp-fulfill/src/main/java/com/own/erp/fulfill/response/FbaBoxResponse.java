package com.own.erp.fulfill.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA箱对外结构(装箱树;箱毛重/外箱尺寸为装箱记录面)
 */
@Builder
public record FbaBoxResponse(

        /** 主键 */
        Long id,

        /** FBA发货单ID */
        Long shipmentId,

        /** 箱号(单内唯一) */
        String boxNo,

        /** 整箱实重(毛重)kg */
        BigDecimal weight,

        /** 外长 cm */
        Integer lengthCm,

        /** 外宽 cm */
        Integer widthCm,

        /** 外高 cm */
        Integer heightCm,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 箱内件(带 SKU 编码) */
        List<FbaBoxItemResponse> items
) {
}
