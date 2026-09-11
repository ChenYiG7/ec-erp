package com.own.erp.inventory.response;

import com.own.erp.inventory.entity.StocktakeOrder;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 盘点单对外结构(docs/07 §1:XxxResponse 只声明允许对外的字段)
 *     record+@Builder(模型可变性分级 docs/07 §1):读侧不可变;from 用 builder 命名传参防相邻同类型字段错位;
 *     详情带明细(withItems wither 副本,同 PurchaseOrderResponse 先例);分页列表不带明细
 */
@Builder
public record StocktakeOrderResponse(

        /** 主键 */
        Long id,

        /** 盘点单号 ST+yyyyMMdd+seq,唯一 */
        String stocktakeNo,

        /** 盘点仓ID(warehouse.id) */
        Long warehouseId,

        /** 盘点范围:ALL全仓/SKU_SET选定SKU集 */
        String scopeType,

        /** DRAFT草稿/COUNTING盘点中/PENDING_ADJUST待调整/ADJUSTED已调整/CLOSED已关闭/CANCELED已取消 */
        String status,

        /** 备注 */
        String remark,

        /** 创建人(sys_user.id) */
        Long createdBy,

        /** 调整确认人(sys_user.id,生成调整时回填) */
        Long confirmedBy,

        /** 调整确认时间(生成调整时回填) */
        LocalDateTime confirmedAt,

        /** 创建时间 */
        LocalDateTime createdAt,

        /** 更新时间 */
        LocalDateTime updatedAt,

        /** 盘点明细(详情带出,分页为 null) */
        List<StocktakeItemResponse> items

) {

    /** 实体 → Response 显式逐字段映射(禁反射拷贝,漏字段编译期可见);items 走 withItems 补挂 */
    public static StocktakeOrderResponse from(StocktakeOrder entity) {
        return StocktakeOrderResponse.builder()
                .id(entity.getId())
                .stocktakeNo(entity.getStocktakeNo())
                .warehouseId(entity.getWarehouseId())
                .scopeType(entity.getScopeType())
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .createdBy(entity.getCreatedBy())
                .confirmedBy(entity.getConfirmedBy())
                .confirmedAt(entity.getConfirmedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /** wither 副本:详情挂明细,禁为补一个字段回退可变模型(docs/07 §1 分级③) */
    public StocktakeOrderResponse withItems(List<StocktakeItemResponse> items) {
        return new StocktakeOrderResponse(id, stocktakeNo, warehouseId, scopeType, status, remark, createdBy,
                confirmedBy, confirmedAt, createdAt, updatedAt, items);
    }
}
