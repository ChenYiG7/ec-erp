package com.own.erp.finance.response;

import com.own.erp.finance.entity.FirstLegBoxItem;
import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 头程箱内件对外结构;skuCode 由 Service 经 GoodsQueryApi 契约批量回填(跨域不 join 实体)
 */
@Builder
public record FirstLegBoxItemResponse(

        /** 主键 */
        Long id,

        /** 箱ID */
        Long boxId,

        /** SKU ID */
        Long skuId,

        /** SKU 编码(契约批量回填;查无显 null,历史单据仍显裸 ID) */
        String skuCode,

        /** 箱内件数 */
        Integer quantity

) {

    /** 实体 → Response(skuCode 缺省由调用方补) */
    public static FirstLegBoxItemResponse from(FirstLegBoxItem entity) {
        return FirstLegBoxItemResponse.builder()
                .id(entity.getId())
                .boxId(entity.getBoxId())
                .skuId(entity.getSkuId())
                .quantity(entity.getQuantity())
                .build();
    }
}
