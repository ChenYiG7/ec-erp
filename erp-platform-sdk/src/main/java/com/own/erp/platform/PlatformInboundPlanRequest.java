package com.own.erp.platform;

import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 入库发货计划生成命令(#35 fba-shipment V2,防腐层平台中立模型):
 *         领域侧(erp-fulfill)按 fba_shipment 计划行装配,平台侧自行拆分建议与目的地仓库,
 *         结果见 {@link PlatformClient#createInboundShipmentPlan}(回填 fba_shipment.platform_shipment_id)。
 *         字段名与 SP-API Fulfillment Inbound Shipment API v0 createInboundShipmentPlan 请求对齐,联调时校准
 */
@Builder
public record PlatformInboundPlanRequest(
        /** 目的国(ISO 3166-1两位,如 US) */
        String shipToCountryCode,
        /** 标签偏好(SELLER_LABEL / AMAZON_LABEL,平台侧枚举字面量) */
        String labelPrepPreference,
        /** 我方发货仓地址(必填) */
        PlatformAddress shipFromAddress,
        /** 计划行(SPU→SKU 数量清单,数量为整箱件数口径随领域侧拍板) */
        List<Item> items) {

    @Builder
    public record Item(
            /** 平台侧 SKU(shop_product_sku.seller_sku) */
            String sellerSku,
            /** 计划数量(正数) */
            int quantity) {
    }
}
