package com.own.erp.purchase.request.command;

import com.own.erp.purchase.entity.PurchaseOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

/**
 * @author : chenyi
 * @Date : 2026/9/3
 * @Description : 采购单写侧入参(docs/07 §1:XxxSaveRequest,创建/更新共用,id 由路径携带不入参)
 *     #10 状态机改造:status 服务端管理(创建固定 DRAFT)、totalAmount 服务端按 Σ(数量×单价) 计算,
 *     二者从入参剔除防"明细与总金额不一致"脏数据;明细整单提交,更新时整体替换
 */
@Builder
public record PurchaseOrderSaveRequest(

        /** 采购单号,唯一 */
        @NotBlank
        @Size(max = 64)
        String poNo,

        /** 供应商ID(supplier.id) */
        @NotNull
        Long supplierId,

        /** 收货仓ID(warehouse.id) */
        @NotNull
        Long warehouseId,

        /** 备注 */
        @Size(max = 255)
        String remark,

        /** 创建人(sys_user.id),前端工程接线 SecurityContext 前暂由调用方传 */
        Long createdBy,

        /** 采购明细(创建/更新整单提交) */
        @NotEmpty
        @Valid
        List<PurchaseOrderItemSaveRequest> items
) {

    /** 请求 → 实体显式逐字段映射(禁反射拷贝);status/totalAmount 由 Service 服务端回填,不入映射 */
    public PurchaseOrder toEntity() {
        return PurchaseOrder.builder()
                .poNo(poNo)
                .supplierId(supplierId)
                .warehouseId(warehouseId)
                .remark(remark)
                .createdBy(createdBy)
                .build();
    }
}
