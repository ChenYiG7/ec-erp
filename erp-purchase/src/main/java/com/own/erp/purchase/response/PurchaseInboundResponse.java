package com.own.erp.purchase.response;

import com.own.erp.purchase.entity.PurchaseInbound;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 采购入库单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     #10:详情带明细(withItems wither 副本,同 ShopOrderResponse 先例);分页列表不带明细
 */
@Builder
public record PurchaseInboundResponse(

        /** 主键 */
        Long id,

        /** 入库单号,唯一 */
        String inboundNo,

        /** 采购单ID(purchase_order.id) */
        Long poId,

        /** 入库仓ID(warehouse.id,服务端取采购单收货仓) */
        Long warehouseId,

        /** PENDING待入库/RECEIVED已入库/CANCELLED已取消 */
        String status,

        /** 备注(数量差异说明等) */
        String remark,

        /** 创建人(sys_user.id) */
        Long createdBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 入库明细(详情带出,分页为 null) */
        List<PurchaseInboundItemResponse> items
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);items 走 withItems 补挂 */
    public static PurchaseInboundResponse from(PurchaseInbound entity) {
        return PurchaseInboundResponse.builder()
                .id(entity.getId())
                .inboundNo(entity.getInboundNo())
                .poId(entity.getPoId())
                .warehouseId(entity.getWarehouseId())
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** wither 副本:详情挂明细,禁为补一个字段回退可变模型(docs/07 §1 分级③) */
    public PurchaseInboundResponse withItems(List<PurchaseInboundItemResponse> items) {
        return new PurchaseInboundResponse(id, inboundNo, poId, warehouseId, status, remark,
                createdBy, createdAt, updatedAt, items);
    }
}
