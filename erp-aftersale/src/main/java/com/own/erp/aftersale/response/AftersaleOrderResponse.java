package com.own.erp.aftersale.response;

import com.own.erp.aftersale.entity.AftersaleOrder;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 售后单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     生成器默认全字段映射:敏感/服务端管理字段必须人工删减,不该出去的字段不建进 Response 即编译期封死
 */
@Builder
public record AftersaleOrderResponse(

        /** 主键 */
        Long id,

        /** 售后单号,唯一 */
        String aftersaleNo,

        /** 店铺ID(shop.id) */
        Long shopId,

        /** 平台退款/售后单ID,幂等键 */
        String platformRefundId,

        /** 关联平台订单ID(shop_order.id) */
        Long orderId,

        /** 退货入库仓ID(warehouse.id,收退件时回填;#12) */
        Long warehouseId,

        /** REFUND_ONLY仅退款/RETURN_REFUND退货退款/EXCHANGE换货/RESEND补发 */
        String type,

        /** PENDING待处理/APPROVED已同意/RETURNING待收退件/RETURN_RECEIVED已收退件/REFUNDED已退款/COMPLETED已完成/REJECTED已拒绝/CANCELLED已取消(#12 状态机 2026-09-04 定版) */
        String status,

        /** 退款金额(原币) */
        BigDecimal refundAmount,

        /** 币种(ISO 4217) */
        String currency,

        /** 售后原因 */
        String reason,

        /** 处理结果 */
        String result,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 实收退货明细(#12;仅详情接口携带,分页不查子表) */
        List<AftersaleReturnItemResponse> returnItems
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见) */
    public static AftersaleOrderResponse from(AftersaleOrder entity) {
        return AftersaleOrderResponse.builder()
                .id(entity.getId())
                .aftersaleNo(entity.getAftersaleNo())
                .shopId(entity.getShopId())
                .platformRefundId(entity.getPlatformRefundId())
                .orderId(entity.getOrderId())
                .warehouseId(entity.getWarehouseId())
                .type(entity.getType())
                .status(entity.getStatus())
                .refundAmount(entity.getRefundAmount())
                .currency(entity.getCurrency())
                .reason(entity.getReason())
                .result(entity.getResult())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** 携带退货明细的副本(wither,docs/07 §1):详情接口构造后补 returnItems 用,分页不调——record 不可变的"构造后补字段"等价写法 */
    public AftersaleOrderResponse withReturnItems(List<AftersaleReturnItemResponse> returnItems) {
        return new AftersaleOrderResponse(id, aftersaleNo, shopId, platformRefundId, orderId, warehouseId, type,
                status, refundAmount, currency, reason, result, createdAt, updatedAt, returnItems);
    }
}
