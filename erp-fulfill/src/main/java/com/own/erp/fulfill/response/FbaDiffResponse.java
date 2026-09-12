package com.own.erp.fulfill.response;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA收货对账差异行对外结构(SHORT缺收/EXTRA多收/OK一致;RECEIVING 态可重复登记覆盖)
 */
@Builder
public record FbaDiffResponse(

        /** 主键 */
        Long id,

        /** FBA发货单ID */
        Long shipmentId,

        /** SKU ID(product_sku.id) */
        Long skuId,

        /** SKU 编码(GoodsQueryApi 契约批量回填) */
        String skuCode,

        /** 发出量(=Σ箱内件) */
        Integer shippedQty,

        /** 平台收货登记量 */
        Integer receivedQty,

        /** 差异类型:SHORT/EXTRA/OK */
        String diffType,

        /** 对账核对时间 */
        LocalDateTime checkedAt
) {
}
