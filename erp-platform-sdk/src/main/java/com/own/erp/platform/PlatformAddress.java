package com.own.erp.platform;

import lombok.Builder;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 入库发货地址(#35 fba-shipment V2,防腐层平台中立模型):
 *         createInboundShipmentPlan 的 ShipFromAddress 要素(我方发货仓地址,领域侧装配)。
 *         字段名与 SP-API Fulfillment Inbound Shipment API v0 Address 模型对齐,联调时校准(docs/07 §8)
 */
@Builder
public record PlatformAddress(
        /** 联系人/仓库名 */
        String name,
        /** 地址行 1(必填) */
        String addressLine1,
        /** 地址行 2(可空) */
        String addressLine2,
        /** 城市(必填) */
        String city,
        /** 州/省(美国站必填) */
        String stateOrRegion,
        /** 邮编 */
        String postalCode,
        /** 国家代码(ISO 3166-1两位,如 US) */
        String countryCode) {
}
