package com.own.erp.finance.response;

import com.own.erp.finance.entity.FirstLegBox;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程箱对外结构(详情装箱树节点);items 经 withItems 填充
 */
@Builder
public record FirstLegBoxResponse(

        /** 主键 */
        Long id,

        /** 头程单ID */
        Long shipmentId,

        /** 箱号 */
        String boxNo,

        /** 整箱实重(毛重)kg */
        BigDecimal weight,

        /** 外长 cm */
        Integer lengthCm,

        /** 外宽 cm */
        Integer widthCm,

        /** 外高 cm */
        Integer heightCm,

        /** 箱内件(withItems 填充) */
        List<FirstLegBoxItemResponse> items

) {

    /** 实体 → Response(不含内件) */
    public static FirstLegBoxResponse from(FirstLegBox entity) {
        return FirstLegBoxResponse.builder()
                .id(entity.getId())
                .shipmentId(entity.getShipmentId())
                .boxNo(entity.getBoxNo())
                .weight(entity.getWeight())
                .lengthCm(entity.getLengthCm())
                .widthCm(entity.getWidthCm())
                .heightCm(entity.getHeightCm())
                .build();
    }

    /** 补箱内件(wither 副本) */
    public FirstLegBoxResponse withItems(List<FirstLegBoxItemResponse> items) {
        return FirstLegBoxResponse.builder()
                .id(id).shipmentId(shipmentId).boxNo(boxNo).weight(weight)
                .lengthCm(lengthCm).widthCm(widthCm).heightCm(heightCm)
                .items(items)
                .build();
    }
}
