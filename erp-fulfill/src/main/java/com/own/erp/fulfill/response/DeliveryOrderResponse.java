package com.own.erp.fulfill.response;

import com.own.erp.fulfill.entity.DeliveryOrder;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 发货单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)。
 *     #11 激活:补 warehouse_id/ship_by_time/created_by 三列出参,详情随单带明细(wither 副本)
 */
@Builder
public record DeliveryOrderResponse(

        /** 主键 */
        Long id,

        /** 发货单号,唯一 */
        String deliveryNo,

        /** 平台订单ID(shop_order.id) */
        Long orderId,

        /** 店铺ID(shop.id,服务端按订单回填) */
        Long shopId,

        /** 出库仓ID(warehouse.id,ship 时从该仓扣库存) */
        Long warehouseId,

        /** MANUAL手工/WAYBILL电子面单/SUPPLIER供应商代发/FBA/OVERSEAS海外仓 */
        String type,

        /** PENDING待发货/SHIPPED已发货/DELIVERED已签收/CANCELLED已取消 */
        String status,

        /** 承诺发货时限(平台侧快照,国内平台考核) */
        LocalDateTime shipByTime,

        /** 物流公司 */
        String logisticsCompany,

        /** 运单号 */
        String trackingNo,

        /** 电子面单文件地址 */
        String waybillUrl,

        /** 发货时间(ship 确认时回写) */
        LocalDateTime shippedAt,

        /** 创建人(sys_user.id) */
        Long createdBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 发货明细(仅详情接口带回) */
        List<DeliveryOrderItemResponse> items
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);items 由 withItems 承接 */
    public static DeliveryOrderResponse from(DeliveryOrder entity) {
        return DeliveryOrderResponse.builder()
                .id(entity.getId())
                .deliveryNo(entity.getDeliveryNo())
                .orderId(entity.getOrderId())
                .shopId(entity.getShopId())
                .warehouseId(entity.getWarehouseId())
                .type(entity.getType())
                .status(entity.getStatus())
                .shipByTime(entity.getShipByTime())
                .logisticsCompany(entity.getLogisticsCompany())
                .trackingNo(entity.getTrackingNo())
                .waybillUrl(entity.getWaybillUrl())
                .shippedAt(entity.getShippedAt())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** wither 副本:详情接口随单带明细(docs/07 §1,record 不回退可变模型) */
    public DeliveryOrderResponse withItems(List<DeliveryOrderItemResponse> items) {
        return new DeliveryOrderResponse(id, deliveryNo, orderId, shopId, warehouseId, type, status,
                shipByTime, logisticsCompany, trackingNo, waybillUrl, shippedAt, createdBy,
                createdAt, updatedAt, items);
    }
}
