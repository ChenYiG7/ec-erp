package com.own.erp.inventory.request.command;

import com.own.erp.inventory.entity.TransferOrder;
import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/11
 * @Description : 调拨单写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)
 *     record+@Builder(模型可变性分级 docs/07 §1);toEntity 用 entity builder 链一次成型(纯构造位,docs/07 §1 分级①)
 *     已剔除服务端管理列(status 固定 DRAFT、created_by 按 SecurityContext);明细整体替换(update 语义)
 */
@Builder
public record TransferOrderSaveRequest(

        /** 调拨单号 TR+yyyyMMdd+seq,唯一 */
        String transferNo,

        /** 调出仓ID(warehouse.id) */
        Long fromWarehouseId,

        /** 调入仓ID(warehouse.id) */
        Long toWarehouseId,

        /** 备注 */
        String remark,

        /** 调拨明细(≥1 行,SKU 不重复,数量 > 0) */
        List<TransferOrderItemSaveRequest> items

) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);服务端管理列不透传(status/createdBy 由 Service 回填) */
    public TransferOrder toEntity() {
        return TransferOrder.builder()
                .transferNo(transferNo)
                .fromWarehouseId(fromWarehouseId)
                .toWarehouseId(toWarehouseId)
                .remark(remark)
                .build();
    }
}
