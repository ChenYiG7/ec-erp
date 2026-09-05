package com.own.erp.purchase.response;

import com.own.erp.purchase.entity.PurchaseOrder;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 采购单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     #10:详情带明细(withItems wither 副本,同 ShopOrderResponse 先例);分页列表不带明细
 */
@Builder
public record PurchaseOrderResponse(

        /** 主键 */
        Long id,

        /** 采购单号,唯一 */
        String poNo,

        /** 供应商ID(supplier.id) */
        Long supplierId,

        /** 收货仓ID(warehouse.id) */
        Long warehouseId,

        /** DRAFT草稿/AUDITED已审核/PARTIAL_RECEIVED部分入库/RECEIVED已入库/CLOSED已关闭 */
        String status,

        /** 采购总金额(本位币,服务端按 Σ(数量×单价) 计算) */
        BigDecimal totalAmount,

        /** 备注 */
        String remark,

        /** 创建人(sys_user.id) */
        Long createdBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 采购明细(详情带出,分页为 null) */
        List<PurchaseOrderItemResponse> items
) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);items 走 withItems 补挂 */
    public static PurchaseOrderResponse from(PurchaseOrder entity) {
        return PurchaseOrderResponse.builder()
                .id(entity.getId())
                .poNo(entity.getPoNo())
                .supplierId(entity.getSupplierId())
                .warehouseId(entity.getWarehouseId())
                .status(entity.getStatus())
                .totalAmount(entity.getTotalAmount())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** wither 副本:详情挂明细,禁为补一个字段回退可变模型(docs/07 §1 分级③) */
    public PurchaseOrderResponse withItems(List<PurchaseOrderItemResponse> items) {
        return new PurchaseOrderResponse(id, poNo, supplierId, warehouseId, status, totalAmount, remark,
                createdBy, createdAt, updatedAt, items);
    }
}
