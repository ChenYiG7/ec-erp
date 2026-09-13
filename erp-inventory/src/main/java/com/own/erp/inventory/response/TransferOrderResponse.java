package com.own.erp.inventory.response;

import com.own.erp.inventory.entity.TransferOrder;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 调拨单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位;
 *     详情带明细(withItems wither 副本,同 PurchaseOrderResponse 先例);分页列表不带明细
 */
@Builder
public record TransferOrderResponse(

        /** 主键 */
        Long id,

        /** 调拨单号 TR+yyyyMMdd+seq,唯一 */
        String transferNo,

        /** 调出仓ID(warehouse.id) */
        Long fromWarehouseId,

        /** 调入仓ID(warehouse.id) */
        Long toWarehouseId,

        /** DRAFT草稿/IN_TRANSIT在途(已发未达)/CONFIRMED已确认(调拨已达)/CANCELED已取消 */
        String status,

        /** 动账模式(#30 余量①):DIRECT确认即达(V1默认)/IN_TRANSIT在途(OUT→到货IN) */
        String transitMode,

        /** 备注 */
        String remark,

        /** 创建人(sys_user.id) */
        Long createdBy,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 调拨明细(详情带出,分页为 null) */
        List<TransferOrderItemResponse> items

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);items 走 withItems 补挂 */
    public static TransferOrderResponse from(TransferOrder entity) {
        return TransferOrderResponse.builder()
                .id(entity.getId())
                .transferNo(entity.getTransferNo())
                .fromWarehouseId(entity.getFromWarehouseId())
                .toWarehouseId(entity.getToWarehouseId())
                .status(entity.getStatus())
                .transitMode(entity.getTransitMode())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** wither 副本:详情挂明细,禁为补一个字段回退可变模型(docs/07 §1 分级③) */
    public TransferOrderResponse withItems(List<TransferOrderItemResponse> items) {
        return new TransferOrderResponse(id, transferNo, fromWarehouseId, toWarehouseId, status, transitMode,
                remark, createdBy, createdAt, updatedAt, items);
    }
}
