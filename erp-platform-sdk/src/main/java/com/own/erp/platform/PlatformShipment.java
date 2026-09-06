package com.own.erp.platform;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/6
 * @Description : 发货回传命令(SPI 层,#3 uploadTracking 2026-09-06 签名收口):
 *         原四散参形态(platformOrderId/trackingNo/logisticsCode)缺行级发运数量与发货时间,
 *         支撑不了 Amazon MFN confirmShipment 的必填要素(orderItems[].quantity/shipDate),
 *         趁单 adapter 窗口改签名(零存量实现迁移成本);@Builder 防相邻同类型字段错位
 *         (同 InventoryChangeCommand 拍板,docs/07 §1 ⑤)。
 *         编排侧(erp-api,#11 ship 后接线)职责:发货明细行 → platformOrderItemId 翻译
 *         (经 ShopOrderApi 契约)后装配本命令;FBA/海外仓平台自履约不回传,不装配。
 */
@Builder
public record PlatformShipment(
        /** 平台订单号(如 AmazonOrderId) */
        String platformOrderId,
        /** 运单号 */
        String trackingNo,
        /** 物流公司编码(平台侧编内承运商,如 Amazon MFN carrierCode);编外承运商可空 */
        String carrierCode,
        /** 物流公司名称(carrierCode 编外兜底);与 carrierCode 至少其一 */
        String carrierName,
        /** 发货时间(平台回传要素,#11 ship 回写 shipped_at 同源) */
        Instant shipTime,
        /** 行级发运明细(未绑定 SKU 行不参与发货,#11 口径) */
        List<Item> items) {

    @Builder
    public record Item(
            /** 平台订单行 ID(shop_order_item.platform_order_item_id) */
            String platformOrderItemId,
            /** 发运数量(delivery_order_item.ship_qty,正数) */
            int quantity) {
    }
}
