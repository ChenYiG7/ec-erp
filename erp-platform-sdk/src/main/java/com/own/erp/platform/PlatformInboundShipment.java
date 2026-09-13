package com.own.erp.platform;

import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/12
 * @Description : FBA 入库发货单平台侧形态(#35 fba-shipment V2,防腐层平台中立模型):
 *         一型三用——createInboundShipmentPlan 结果(平台拆分建议,quantityPlanned 有值)/
 *         pullInboundShipments 状态拉取(status + quantityReceived 有值,驱动 fba_shipment_diff 对账)。
 *         字段名与 SP-API Fulfillment Inbound Shipment API v0 对齐,联调时校准(docs/07 §8)
 */
@Builder
public record PlatformInboundShipment(
        /** 平台发货单 ID(回填 fba_shipment.platform_shipment_id) */
        String shipmentId,
        /** 目的地 FBA 仓代码(如 PHX7,平台拆分建议产出) */
        String destinationFulfillmentCenter,
        /** 平台侧状态(如 WORKING/SHIPPED/IN_PROGRESS/RECEIVED/CLOSED,平台枚举字面量) */
        String status,
        /** 行级明细(按来源携带不同数量语义,可空项注释见 {@link Item}) */
        List<Item> items) {

    @Builder
    public record Item(
            /** 平台侧 SKU */
            String sellerSku,
            /** 计划分配量(createInboundShipmentPlan 结果携带) */
            Integer quantityPlanned,
            /** 我方发出申报量(shipmentItems 接口 QuantityShipped) */
            Integer quantityShipped,
            /** 平台实收量(shipmentItems 接口 QuantityReceived,fba_shipment_diff 对账核心) */
            Integer quantityReceived) {
    }
}
